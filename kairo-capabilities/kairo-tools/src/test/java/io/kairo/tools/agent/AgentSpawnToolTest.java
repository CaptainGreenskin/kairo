/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.tools.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.agent.SubagentDefinition;
import io.kairo.api.agent.SubagentRegistry;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.DefaultToolRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AgentSpawnToolTest {

    private AgentSpawnTool tool;
    private StubAgentFactory agentFactory;
    private AgentConfig baseConfig;
    private ToolRegistry parentToolRegistry;
    private StubSubagentRegistry subagentRegistry;
    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    @BeforeEach
    void setUp() {
        // Parent registry stocked with three named tools so whitelist-filter
        // tests can assert which ones survive into the child config.
        parentToolRegistry = new DefaultToolRegistry();
        parentToolRegistry.register(stubToolDef("read"));
        parentToolRegistry.register(stubToolDef("grep"));
        parentToolRegistry.register(stubToolDef("bash"));

        baseConfig =
                AgentConfig.builder()
                        .name("parent")
                        .systemPrompt("parent prompt")
                        .modelProvider(new StubModelProvider())
                        .modelName("parent-model")
                        .maxIterations(10)
                        .timeout(java.time.Duration.ofMinutes(5))
                        .tokenBudget(100_000)
                        .toolRegistry(parentToolRegistry)
                        .build();
        agentFactory = new StubAgentFactory();
        subagentRegistry = new StubSubagentRegistry();
        tool = new AgentSpawnTool(agentFactory, baseConfig, subagentRegistry);
    }

    private static ToolDefinition stubToolDef(String name) {
        return new ToolDefinition(
                name,
                "stub " + name,
                ToolCategory.GENERAL,
                new JsonSchema("object", Map.of(), List.of(), name + " input"),
                AgentSpawnToolTest.class);
    }

    @Test
    void missingNameParameter() {
        // After SA1, the error message points users at both invocation modes
        // (registered via subagent_type or ad-hoc via name) — assert on the
        // common substring.
        ToolResult result = tool.execute(Map.of("task", "do something"), CTX).block();
        assertTrue(result.isError());
        assertTrue(
                result.content().contains("'subagent_type' or 'name'"), "got: " + result.content());
    }

    @Test
    void blankNameParameter() {
        ToolResult result =
                tool.execute(Map.of("name", "   ", "task", "do something"), CTX).block();
        assertTrue(result.isError());
        assertTrue(
                result.content().contains("'subagent_type' or 'name'"), "got: " + result.content());
    }

    @Test
    void missingTaskParameter() {
        ToolResult result = tool.execute(Map.of("name", "sub-agent"), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("'task' is required"));
    }

    @Test
    void blankTaskParameter() {
        ToolResult result = tool.execute(Map.of("name", "sub-agent", "task", "   "), CTX).block();
        assertTrue(result.isError());
        assertTrue(result.content().contains("'task' is required"));
    }

    @Test
    void spawnWithTextResult() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "Task completed successfully");

        ToolResult result =
                tool.execute(Map.of("name", "worker", "task", "process data"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("worker"));
        assertTrue(result.content().contains("Task completed successfully"));
        assertEquals("worker", result.metadata().get("subAgentName"));
    }

    @Test
    void spawnWithCustomSystemPrompt() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(
                        Map.of(
                                "name",
                                "worker",
                                "task",
                                "do work",
                                "systemPrompt",
                                "You are a specialized agent."),
                        CTX)
                .block();

        assertEquals("You are a specialized agent.", agentFactory.lastConfig.systemPrompt());
    }

    @Test
    void spawnWithDefaultSystemPrompt() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("name", "worker", "task", "do work"), CTX).block();

        assertEquals("You are a helpful sub-agent.", agentFactory.lastConfig.systemPrompt());
    }

    @Test
    void spawnWithNullResult() {
        agentFactory.response = null;

        ToolResult result = tool.execute(Map.of("name", "worker", "task", "do work"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("completed with no output"));
    }

    @Test
    void spawnWithMultipleContentBlocks() {
        agentFactory.response =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.TextContent("part 1"))
                        .addContent(new Content.TextContent("part 2"))
                        .build();

        ToolResult result =
                tool.execute(Map.of("name", "worker", "task", "multi-part task"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("part 1"));
        assertTrue(result.content().contains("part 2"));
    }

    @Test
    void spawnWithNonTextContentFallsBackToMsgToString() {
        agentFactory.response =
                Msg.builder()
                        .role(MsgRole.ASSISTANT)
                        .addContent(new Content.ToolResultContent("id1", "result text", false))
                        .build();

        ToolResult result =
                tool.execute(Map.of("name", "worker", "task", "tool task"), CTX).block();

        assertFalse(result.isError());
        assertNotNull(result.content());
    }

    @Test
    void spawnExceptionReturnsError() {
        agentFactory.shouldThrow = true;

        ToolResult result =
                tool.execute(Map.of("name", "broken", "task", "will fail"), CTX).block();

        assertTrue(result.isError());
        assertTrue(result.content().contains("broken"));
        assertTrue(result.content().contains("Factory failed"));
    }

    @Test
    void spawnAgentCallExceptionReturnsError() {
        agentFactory.shouldFailCall = true;

        ToolResult result = tool.execute(Map.of("name", "failing", "task", "crash"), CTX).block();

        assertTrue(result.isError());
        assertTrue(result.content().contains("failing"));
        assertTrue(result.content().contains("Agent call failed"));
    }

    @Test
    void subAgentConfigInheritsFromBase() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("name", "worker", "task", "do work"), CTX).block();

        assertNotNull(agentFactory.lastConfig);
        assertEquals("worker", agentFactory.lastConfig.name());
        assertEquals(10, agentFactory.lastConfig.maxIterations());
        assertEquals(100_000, agentFactory.lastConfig.tokenBudget());
    }

    @Test
    void taskMessageSentToSubAgent() {
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("name", "worker", "task", "specific task description"), CTX).block();

        assertNotNull(agentFactory.lastStubAgent);
        assertEquals("specific task description", agentFactory.lastStubAgent.lastInputText);
    }

    // ── SA1+SA2: registry path coverage ─────────────────────────────────────

    @Test
    void subagentTypeHitsRegistryAndUsesItsSystemPrompt() {
        // subagent_type wins over any name/systemPrompt args — registry is the
        // source of truth once the agents/*.md contribution is loaded.
        subagentRegistry.register(
                new SubagentDefinition(
                        "code-reviewer",
                        "reviews code diffs",
                        "You are a meticulous code reviewer.",
                        List.of(),
                        null,
                        null));
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "lgtm");

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "subagent_type",
                                        "code-reviewer",
                                        "task",
                                        "review PR #42",
                                        "name",
                                        "ignored-adhoc-name",
                                        "systemPrompt",
                                        "ignored ad-hoc prompt"),
                                CTX)
                        .block();

        assertFalse(result.isError(), () -> "got: " + result.content());
        assertEquals("code-reviewer", agentFactory.lastConfig.name());
        assertEquals("You are a meticulous code reviewer.", agentFactory.lastConfig.systemPrompt());
        assertEquals("registered", result.metadata().get("mode"));
    }

    @Test
    void subagentTypeToolsWhitelistFiltersChildRegistry() {
        subagentRegistry.register(
                new SubagentDefinition(
                        "reader",
                        "read-only inspector",
                        "Inspect files.",
                        List.of("read", "grep"),
                        null,
                        null));
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("subagent_type", "reader", "task", "inspect"), CTX).block();

        ToolRegistry childRegistry = agentFactory.lastConfig.toolRegistry();
        assertNotNull(childRegistry);
        assertTrue(childRegistry.get("read").isPresent(), "whitelisted tool 'read' missing");
        assertTrue(childRegistry.get("grep").isPresent(), "whitelisted tool 'grep' missing");
        assertTrue(childRegistry.get("bash").isEmpty(), "non-whitelisted 'bash' leaked through");
    }

    @Test
    void subagentTypeEmptyToolsWhitelistInheritsParentRegistry() {
        subagentRegistry.register(
                new SubagentDefinition(
                        "explorer",
                        "general explorer",
                        "Explore freely.",
                        List.of(), // empty → inherit
                        null,
                        null));
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("subagent_type", "explorer", "task", "explore"), CTX).block();

        // Empty whitelist = inherit verbatim — child sees the same ToolRegistry
        // instance as the parent, not a filtered copy.
        assertSame(parentToolRegistry, agentFactory.lastConfig.toolRegistry());
    }

    @Test
    void subagentTypeUnknownToolInWhitelistSilentlyDropped() {
        // Lenient on stale agents/*.md — log a warn but don't abort the spawn.
        subagentRegistry.register(
                new SubagentDefinition(
                        "partial",
                        "with a ghost tool",
                        "You have one valid tool.",
                        List.of("read", "does-not-exist"),
                        null,
                        null));
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        ToolResult result =
                tool.execute(Map.of("subagent_type", "partial", "task", "do work"), CTX).block();

        assertFalse(result.isError());
        ToolRegistry childRegistry = agentFactory.lastConfig.toolRegistry();
        assertTrue(childRegistry.get("read").isPresent());
        assertTrue(childRegistry.get("does-not-exist").isEmpty());
    }

    @Test
    void subagentTypeModelPinningOverridesParentModelName() {
        subagentRegistry.register(
                new SubagentDefinition(
                        "haiku-bot",
                        "fast cheap subagent",
                        "Be terse.",
                        List.of(),
                        "claude-haiku-4-5-20251001",
                        null));
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("subagent_type", "haiku-bot", "task", "summarize"), CTX).block();

        assertEquals("claude-haiku-4-5-20251001", agentFactory.lastConfig.modelName());
    }

    @Test
    void subagentTypeNoModelInheritsParentModelName() {
        subagentRegistry.register(
                new SubagentDefinition(
                        "inheriting",
                        "no model pin",
                        "Use the parent's model.",
                        List.of(),
                        null,
                        null));
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        tool.execute(Map.of("subagent_type", "inheriting", "task", "do work"), CTX).block();

        assertEquals("parent-model", agentFactory.lastConfig.modelName());
    }

    @Test
    void subagentTypeNotFoundReturnsErrorWithAvailableList() {
        subagentRegistry.register(
                new SubagentDefinition("alpha", "a", "Alpha agent.", List.of(), null, null));
        subagentRegistry.register(
                new SubagentDefinition("beta", "b", "Beta agent.", List.of(), null, "myplugin"));

        ToolResult result =
                tool.execute(Map.of("subagent_type", "missing", "task", "do work"), CTX).block();

        assertTrue(result.isError());
        // Message must name what was missing AND what's available — that's the
        // "loud failure" promised by SA1 so plugin authors can fix typos fast.
        assertTrue(result.content().contains("missing"), () -> "got: " + result.content());
        assertTrue(result.content().contains("alpha"), () -> "got: " + result.content());
        assertTrue(result.content().contains("myplugin:beta"), () -> "got: " + result.content());
    }

    @Test
    void subagentTypeWithoutRegistryWiredReturnsDescriptiveError() {
        // Legacy 2-arg constructor: registry is null → subagent_type can't be
        // resolved. Spell out both the cause and the workaround.
        AgentSpawnTool registrylessTool = new AgentSpawnTool(agentFactory, baseConfig);

        ToolResult result =
                registrylessTool
                        .execute(Map.of("subagent_type", "code-reviewer", "task", "review"), CTX)
                        .block();

        assertTrue(result.isError());
        assertTrue(
                result.content().contains("no SubagentRegistry"), () -> "got: " + result.content());
        assertTrue(
                result.content().contains("3-arg constructor")
                        || result.content().contains("ad-hoc"),
                () -> "got: " + result.content());
    }

    @Test
    void subagentTypeWithNamespacedQualifiedNameResolves() {
        subagentRegistry.register(
                new SubagentDefinition(
                        "explorer",
                        "namespaced explorer",
                        "Plugin-contributed explorer.",
                        List.of(),
                        null,
                        "myplugin"));
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        ToolResult result =
                tool.execute(Map.of("subagent_type", "myplugin:explorer", "task", "explore"), CTX)
                        .block();

        assertFalse(result.isError(), () -> "got: " + result.content());
        assertEquals("explorer", agentFactory.lastConfig.name());
        assertEquals("Plugin-contributed explorer.", agentFactory.lastConfig.systemPrompt());
    }

    @Test
    void parallelSpawnDoesNotBlockReactorThread() throws Exception {
        // SA4 regression guard: two spawns running concurrently should both
        // complete before the latches release in opposite order. The old
        // .block() impl would deadlock because spawn 1 would hold the worker
        // thread waiting for its agent, never letting spawn 2 even start.
        java.util.concurrent.CountDownLatch agent1Started =
                new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch agent2Started =
                new java.util.concurrent.CountDownLatch(1);

        StubAgentFactory factory1 = new StubAgentFactory();
        factory1.gateLatch = agent2Started; // agent1 waits until agent2 has started
        factory1.signalLatch = agent1Started;
        factory1.response = Msg.of(MsgRole.ASSISTANT, "agent1 done");

        StubAgentFactory factory2 = new StubAgentFactory();
        factory2.gateLatch = agent1Started; // agent2 waits until agent1 has started
        factory2.signalLatch = agent2Started;
        factory2.response = Msg.of(MsgRole.ASSISTANT, "agent2 done");

        AgentSpawnTool tool1 = new AgentSpawnTool(factory1, baseConfig, subagentRegistry);
        AgentSpawnTool tool2 = new AgentSpawnTool(factory2, baseConfig, subagentRegistry);

        // Subscribe on bounded-elastic so each agent.call() can park its
        // thread without starving the other.
        Mono<ToolResult> r1 =
                tool1.execute(Map.of("name", "a1", "task", "do work"), CTX)
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
        Mono<ToolResult> r2 =
                tool2.execute(Map.of("name", "a2", "task", "do work"), CTX)
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());

        // mergeSequential subscribes to both immediately. With .block() in
        // the old impl the second subscription would never get a turn.
        List<ToolResult> results =
                reactor.core.publisher.Flux.merge(r1, r2)
                        .collectList()
                        .block(java.time.Duration.ofSeconds(5));

        assertNotNull(results);
        assertEquals(2, results.size());
        assertFalse(results.get(0).isError());
        assertFalse(results.get(1).isError());
    }

    @Test
    void adhocModeMetadataReportsAdHoc() {
        // Regression guard: the mode metadata distinguishes the two paths so
        // observability/dashboards can tell registered subagent runs apart
        // from ad-hoc one-shots.
        agentFactory.response = Msg.of(MsgRole.ASSISTANT, "done");

        ToolResult result =
                tool.execute(Map.of("name", "ad-hoc-worker", "task", "do work"), CTX).block();

        assertEquals("ad-hoc", result.metadata().get("mode"));
    }

    /** Stub implementation of AgentFactory for testing without Mockito. */
    private static class StubAgentFactory implements AgentFactory {
        Msg response;
        AgentConfig lastConfig;
        StubAgent lastStubAgent;
        boolean shouldThrow;
        boolean shouldFailCall;
        // Concurrency hooks: signalLatch is counted down when call() starts;
        // gateLatch is awaited before call() returns. Lets a test set up an
        // interleaving that only completes if both spawns are running.
        java.util.concurrent.CountDownLatch signalLatch;
        java.util.concurrent.CountDownLatch gateLatch;

        @Override
        public Agent create(AgentConfig config) {
            if (shouldThrow) {
                throw new RuntimeException("Factory failed");
            }
            this.lastConfig = config;
            this.lastStubAgent = new StubAgent(response, shouldFailCall, signalLatch, gateLatch);
            return lastStubAgent;
        }

        @Override
        public Agent createSubAgent(Agent parent, AgentConfig config) {
            return create(config);
        }
    }

    /** Stub implementation of Agent for testing without Mockito. */
    private static class StubAgent implements Agent {
        private final Msg response;
        private final boolean shouldFail;
        private final java.util.concurrent.CountDownLatch signalLatch;
        private final java.util.concurrent.CountDownLatch gateLatch;
        String lastInputText;

        StubAgent(Msg response, boolean shouldFail) {
            this(response, shouldFail, null, null);
        }

        StubAgent(
                Msg response,
                boolean shouldFail,
                java.util.concurrent.CountDownLatch signalLatch,
                java.util.concurrent.CountDownLatch gateLatch) {
            this.response = response;
            this.shouldFail = shouldFail;
            this.signalLatch = signalLatch;
            this.gateLatch = gateLatch;
        }

        @Override
        public reactor.core.publisher.Mono<Msg> call(Msg input) {
            this.lastInputText = input.text();
            if (shouldFail) {
                return reactor.core.publisher.Mono.error(new RuntimeException("Agent call failed"));
            }
            if (signalLatch == null && gateLatch == null) {
                return reactor.core.publisher.Mono.justOrEmpty(response);
            }
            // Concurrency-aware path: defer so the latch dance happens on
            // subscription, not eagerly during call(). Without defer the
            // await blocks the caller before the second spawn even subscribes.
            return reactor.core.publisher.Mono.defer(
                    () -> {
                        if (signalLatch != null) {
                            signalLatch.countDown();
                        }
                        if (gateLatch != null) {
                            try {
                                if (!gateLatch.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                                    return reactor.core.publisher.Mono.error(
                                            new RuntimeException("gate latch timeout"));
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return reactor.core.publisher.Mono.error(e);
                            }
                        }
                        return reactor.core.publisher.Mono.justOrEmpty(response);
                    });
        }

        @Override
        public String id() {
            return "stub-agent";
        }

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public io.kairo.api.agent.AgentState state() {
            return io.kairo.api.agent.AgentState.IDLE;
        }

        @Override
        public void interrupt() {}
    }

    /** In-memory stub SubagentRegistry — no Mockito, mirrors DefaultSubagentRegistry behavior. */
    private static class StubSubagentRegistry implements SubagentRegistry {
        private final ConcurrentMap<String, SubagentDefinition> byQualifiedName =
                new ConcurrentHashMap<>();

        @Override
        public void register(SubagentDefinition definition) {
            SubagentDefinition prior =
                    byQualifiedName.putIfAbsent(definition.qualifiedName(), definition);
            if (prior != null) {
                throw new IllegalStateException(
                        "Duplicate subagent: " + definition.qualifiedName());
            }
        }

        @Override
        public boolean unregister(String qualifiedName) {
            return byQualifiedName.remove(qualifiedName) != null;
        }

        @Override
        public Optional<SubagentDefinition> get(String qualifiedName) {
            return Optional.ofNullable(byQualifiedName.get(qualifiedName));
        }

        @Override
        public List<SubagentDefinition> list() {
            return List.copyOf(byQualifiedName.values());
        }

        @Override
        public List<SubagentDefinition> listByNamespace(String namespace) {
            return byQualifiedName.values().stream()
                    .filter(d -> namespace.equals(d.namespace()))
                    .toList();
        }

        @Override
        public int size() {
            return byQualifiedName.size();
        }
    }

    /** Minimal stub ModelProvider for constructing AgentConfig. */
    private static class StubModelProvider implements ModelProvider {
        @Override
        public reactor.core.publisher.Mono<ModelResponse> call(
                java.util.List<Msg> messages, ModelConfig config) {
            return reactor.core.publisher.Mono.empty();
        }

        @Override
        public reactor.core.publisher.Flux<ModelResponse> stream(
                java.util.List<Msg> messages, ModelConfig config) {
            return reactor.core.publisher.Flux.empty();
        }

        @Override
        public String name() {
            return "stub";
        }
    }
}
