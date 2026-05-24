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
package io.kairo.plugin.component;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.agent.AgentState;
import io.kairo.api.agent.SubagentDefinition;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.plugin.PluginComponent;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.plugin.DefaultSubagentRegistry;
import io.kairo.tools.agent.AgentSpawnTool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * End-to-end SA5 proof: a real {@code agents/*.md} file on disk flows through the loader → markdown
 * parser → {@link DefaultSubagentRegistry} → {@link AgentSpawnTool}, and every frontmatter field
 * (systemPrompt body, {@code tools} whitelist, {@code model} pin) lands on the child {@link
 * AgentConfig} the factory receives.
 *
 * <p>This test stitches together three modules — kairo-plugin (loader + parser + registry),
 * kairo-core (tool registry), kairo-tools (AgentSpawnTool) — so it lives in the plugin module which
 * sits at the integration boundary, with test-scope dependencies on the other two.
 */
class SubagentSpawnE2ETest {

    @Test
    void agentMarkdown_flowsThroughLoaderParserRegistry_intoAgentSpawnToolChildConfig(
            @TempDir Path pluginRoot) throws IOException {
        // 1. Lay down a realistic agents/code-reviewer.md the way a plugin author would.
        Path agentsDir = Files.createDirectory(pluginRoot.resolve("agents"));
        Files.writeString(
                agentsDir.resolve("code-reviewer.md"),
                """
                ---
                name: code-reviewer
                description: Reviews code diffs and flags issues
                model: claude-haiku-4-5-20251001
                tools:
                  - read
                  - grep
                ---
                You are a meticulous code reviewer.

                Read the diff and report issues by severity.
                """);

        // 2. Loader → AgentComponent list
        AgentComponentLoader loader = new AgentComponentLoader();
        List<PluginComponent.AgentComponent> components = loader.load(pluginRoot, "review-plugin");
        assertThat(components).hasSize(1);
        assertThat(components.get(0).name()).isEqualTo("code-reviewer");

        // 3. Parser → SubagentDefinition
        SubagentMarkdownParser parser = new SubagentMarkdownParser();
        SubagentDefinition def = parser.parse(components.get(0).agentFile(), "review-plugin");
        assertThat(def.name()).isEqualTo("code-reviewer");
        assertThat(def.namespace()).isEqualTo("review-plugin");
        assertThat(def.qualifiedName()).isEqualTo("review-plugin:code-reviewer");
        assertThat(def.model()).isEqualTo("claude-haiku-4-5-20251001");
        assertThat(def.tools()).containsExactly("read", "grep");
        assertThat(def.systemPrompt()).contains("meticulous code reviewer");

        // 4. Registry — register the parsed def
        DefaultSubagentRegistry registry = new DefaultSubagentRegistry();
        registry.register(def);
        assertThat(registry.get("review-plugin:code-reviewer")).isPresent();

        // 5. Parent AgentConfig carrying a tool registry with 3 tools — only
        //    'read' + 'grep' should survive into the child per the markdown whitelist.
        ToolRegistry parentTools = new DefaultToolRegistry();
        parentTools.register(stubToolDef("read"));
        parentTools.register(stubToolDef("grep"));
        parentTools.register(stubToolDef("bash"));

        AgentConfig parentConfig =
                AgentConfig.builder()
                        .name("parent")
                        .systemPrompt("parent prompt")
                        .modelProvider(new StubModelProvider())
                        .modelName("parent-model")
                        .maxIterations(10)
                        .timeout(Duration.ofMinutes(5))
                        .tokenBudget(100_000)
                        .toolRegistry(parentTools)
                        .build();

        // 6. AgentSpawnTool wired with the registry — this is what kairo-code
        //    will do in production once the plugin loader feeds the registry.
        CapturingAgentFactory factory = new CapturingAgentFactory();
        AgentSpawnTool tool = new AgentSpawnTool(factory, parentConfig, registry);

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "subagent_type",
                                        "review-plugin:code-reviewer",
                                        "task",
                                        "review PR #42"),
                                new ToolContext("agent-1", "sess-1", Map.of()))
                        .block();

        // 7. Tool succeeded + reported registered mode
        assertThat(result).isNotNull();
        assertThat(result.isError()).isFalse();
        assertThat(result.metadata().get("mode")).isEqualTo("registered");

        // 8. Verify every plugin-contributed field landed on the child config —
        //    this is the actual SA5 contract: agents/*.md is the source of truth.
        AgentConfig childConfig = factory.lastConfig;
        assertThat(childConfig).isNotNull();
        assertThat(childConfig.name()).isEqualTo("code-reviewer");
        assertThat(childConfig.systemPrompt()).contains("meticulous code reviewer");
        assertThat(childConfig.modelName()).isEqualTo("claude-haiku-4-5-20251001");

        ToolRegistry childTools = childConfig.toolRegistry();
        assertThat(childTools).isNotNull();
        assertThat(childTools.get("read")).isPresent();
        assertThat(childTools.get("grep")).isPresent();
        assertThat(childTools.get("bash"))
                .as("bash was not in the agents/*.md whitelist; must not leak")
                .isEmpty();
    }

    private static ToolDefinition stubToolDef(String name) {
        return new ToolDefinition(
                name,
                "stub " + name,
                ToolCategory.GENERAL,
                new JsonSchema("object", Map.of(), List.of(), name + " input"),
                SubagentSpawnE2ETest.class);
    }

    /** Captures the AgentConfig handed to create() so the test can assert on it. */
    private static class CapturingAgentFactory implements AgentFactory {
        AgentConfig lastConfig;

        @Override
        public Agent create(AgentConfig config) {
            this.lastConfig = config;
            return new CapturedAgent();
        }

        @Override
        public Agent createSubAgent(Agent parent, AgentConfig config) {
            return create(config);
        }
    }

    private static class CapturedAgent implements Agent {
        @Override
        public Mono<Msg> call(Msg input) {
            return Mono.just(Msg.of(MsgRole.ASSISTANT, "review complete"));
        }

        @Override
        public String id() {
            return "captured-agent";
        }

        @Override
        public String name() {
            return "captured";
        }

        @Override
        public AgentState state() {
            return AgentState.IDLE;
        }

        @Override
        public void interrupt() {}
    }

    private static class StubModelProvider implements ModelProvider {
        @Override
        public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
            return Mono.empty();
        }

        @Override
        public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
            return Flux.empty();
        }

        @Override
        public String name() {
            return "stub";
        }
    }
}
