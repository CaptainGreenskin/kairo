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

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.agent.SubagentDefinition;
import io.kairo.api.agent.SubagentRegistry;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.tool.DefaultToolRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Spawns a sub-agent to handle a specific task autonomously.
 *
 * <p>Two invocation modes (both backward-compatible):
 *
 * <ol>
 *   <li><b>Registered subagent</b> — pass {@code subagent_type=<qualifiedName>} (e.g. {@code
 *       "code-reviewer"} or {@code "myplugin:explorer"}). Loads the {@link SubagentDefinition} from
 *       the {@link SubagentRegistry} and applies its {@code systemPrompt} + {@code tools} whitelist
 *       + {@code model} pinning. This is the path that lights up {@code agents/*.md} contributions
 *       from plugins.
 *   <li><b>Ad-hoc</b> — pass {@code name}+{@code task}+{@code systemPrompt}. Spawns a sub-agent
 *       inheriting the parent's tools + model. The legacy path; kept so existing callers don't
 *       break.
 * </ol>
 *
 * <p>The sub-agent runs with a clean context (no parent {@code conversationHistory} inherited —
 * {@link AgentFactory#create} is used, not {@code createSubAgent}). The result is returned
 * synchronously to the caller.
 */
@Tool(
        name = "agent_spawn",
        description =
                "Spawn a sub-agent to handle a specific task autonomously. Pass subagent_type to use"
                        + " a registered subagent (with its own systemPrompt + tool whitelist + model);"
                        + " otherwise pass name+task+systemPrompt for an ad-hoc sub-agent.",
        category = ToolCategory.AGENT_AND_TASK)
public class AgentSpawnTool implements SyncTool {

    private static final Logger log = LoggerFactory.getLogger(AgentSpawnTool.class);

    @ToolParam(
            description =
                    "Qualified subagent name (e.g. 'code-reviewer' or 'myplugin:explorer'). Looked"
                            + " up in SubagentRegistry. If provided, overrides name/systemPrompt and"
                            + " applies the subagent's tool whitelist + model pinning.")
    private String subagent_type;

    @ToolParam(description = "Name for the sub-agent (ad-hoc mode; ignored if subagent_type set)")
    private String name;

    @ToolParam(description = "Task description for the sub-agent", required = true)
    private String task;

    @ToolParam(
            description =
                    "System prompt for the sub-agent (ad-hoc mode; ignored if subagent_type set)")
    private String systemPrompt;

    private final AgentFactory agentFactory;
    private final AgentConfig baseConfig;
    private final SubagentRegistry subagentRegistry;

    /**
     * Legacy constructor — registry-less. Ad-hoc mode only; {@code subagent_type} lookups will fail
     * loudly with a descriptive error.
     */
    public AgentSpawnTool(AgentFactory agentFactory, AgentConfig baseConfig) {
        this(agentFactory, baseConfig, null);
    }

    /**
     * @param agentFactory the factory for creating sub-agents
     * @param baseConfig the parent agent's config used as a template
     * @param subagentRegistry catalog of plugin-contributed subagents (nullable)
     */
    public AgentSpawnTool(
            AgentFactory agentFactory, AgentConfig baseConfig, SubagentRegistry subagentRegistry) {
        this.agentFactory = agentFactory;
        this.baseConfig = baseConfig;
        this.subagentRegistry = subagentRegistry;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        String subagentType = (String) args.get("subagent_type");
        String adhocName = (String) args.get("name");
        String taskText = (String) args.get("task");

        if (taskText == null || taskText.isBlank()) {
            return Mono.just(ToolResult.error(null, "Parameter 'task' is required"));
        }

        // Resolve the subagent definition synchronously — cheap registry
        // lookup + arg validation. Failures here short-circuit to a Mono.just
        // error so concurrent spawns don't all wedge on the same bad input.
        ResolvedSubagent resolved;
        if (subagentType != null && !subagentType.isBlank()) {
            if (subagentRegistry == null) {
                return Mono.just(
                        ToolResult.error(
                                null,
                                "subagent_type='"
                                        + subagentType
                                        + "' but no SubagentRegistry is wired into this"
                                        + " AgentSpawnTool — pass one to the 3-arg constructor or"
                                        + " use the ad-hoc name/systemPrompt parameters instead."));
            }
            Optional<SubagentDefinition> def = subagentRegistry.get(subagentType);
            if (def.isEmpty()) {
                return Mono.just(
                        ToolResult.error(
                                null,
                                "subagent_type='"
                                        + subagentType
                                        + "' not found in SubagentRegistry. Registered: "
                                        + subagentRegistry.list().stream()
                                                .map(SubagentDefinition::qualifiedName)
                                                .toList()));
            }
            resolved = ResolvedSubagent.fromDefinition(def.get());
        } else {
            String prompt =
                    (String) args.getOrDefault("systemPrompt", "You are a helpful sub-agent.");
            if (adhocName == null || adhocName.isBlank()) {
                return Mono.just(
                        ToolResult.error(
                                null, "Either 'subagent_type' or 'name' parameter is required"));
            }
            resolved = new ResolvedSubagent(adhocName, prompt, List.of(), null);
        }

        final ResolvedSubagent r = resolved;
        final String mode = subagentType != null ? "registered" : "ad-hoc";
        final String modeLog = subagentType != null ? "registered:" + subagentType : "ad-hoc";

        // Fully reactive pipeline — no .block(). When ToolExecutor.executeParallel
        // fan-outs multiple spawns, each subAgent.call() runs concurrently on
        // its own subscription rather than serializing on this thread.
        return Mono.fromCallable(
                        () -> {
                            AgentConfig subConfig = buildSubagentConfig(r);
                            Agent subAgent = agentFactory.create(subConfig);
                            log.info(
                                    "Spawned sub-agent '{}' (mode={}) for task: {}",
                                    r.name(),
                                    modeLog,
                                    taskText);
                            return subAgent;
                        })
                .flatMap(
                        subAgent -> {
                            Msg taskMsg = Msg.of(MsgRole.USER, taskText);
                            return subAgent.call(taskMsg)
                                    .map(result -> successResult(r, result, mode))
                                    .switchIfEmpty(Mono.fromSupplier(() -> emptyResult(r, mode)));
                        })
                .onErrorResume(e -> Mono.just(failureResult(r, e)));
    }

    private ToolResult successResult(ResolvedSubagent r, Msg result, String mode) {
        log.info("Sub-agent '{}' completed", r.name());
        return ToolResult.success(
                null,
                String.format("Sub-agent '%s' result:\n%s", r.name(), extractText(result)),
                Map.of("subAgentName", r.name(), "mode", mode));
    }

    private ToolResult emptyResult(ResolvedSubagent r, String mode) {
        log.info("Sub-agent '{}' completed", r.name());
        return ToolResult.success(
                null,
                String.format(
                        "Sub-agent '%s' result:\nSub-agent completed with no output", r.name()),
                Map.of("subAgentName", r.name(), "mode", mode));
    }

    private ToolResult failureResult(ResolvedSubagent r, Throwable e) {
        log.error("Sub-agent '{}' failed: {}", r.name(), e.getMessage(), e);
        return ToolResult.error(
                null, String.format("Sub-agent '%s' failed: %s", r.name(), e.getMessage()));
    }

    /** Build the sub-agent's {@link AgentConfig} with tool whitelist + model pinning applied. */
    private AgentConfig buildSubagentConfig(ResolvedSubagent resolved) {
        AgentConfig.Builder builder =
                AgentConfig.builder()
                        .name(resolved.name())
                        .systemPrompt(resolved.systemPrompt())
                        .modelProvider(baseConfig.modelProvider())
                        .maxIterations(baseConfig.maxIterations())
                        .timeout(baseConfig.timeout())
                        .tokenBudget(baseConfig.tokenBudget());

        // tools whitelist: when non-empty, filter the parent's tool registry to
        // only the named tools. Empty = inherit everything. Unknown tools are
        // silently dropped (log a warning) rather than failing the spawn —
        // matches Claude Code's lenient behavior on stale agent.md frontmatter.
        ToolRegistry parentTools = baseConfig.toolRegistry();
        if (resolved.tools().isEmpty() || parentTools == null) {
            builder.toolRegistry(parentTools);
        } else {
            builder.toolRegistry(filteredToolRegistry(parentTools, resolved.tools()));
        }

        // model pinning: when non-null, override the model name. When null,
        // inherit the parent's modelName so subagents run on the same model
        // unless they explicitly opt out. The provider factory still comes
        // from the parent — switching providers per-model would require a
        // ProviderRegistry handle here; deferred to a future refactor.
        if (resolved.model() != null && !resolved.model().isBlank()) {
            builder.modelName(resolved.model());
        } else if (baseConfig.modelName() != null) {
            builder.modelName(baseConfig.modelName());
        }

        return builder.build();
    }

    private static ToolRegistry filteredToolRegistry(ToolRegistry parent, List<String> whitelist) {
        DefaultToolRegistry filtered = new DefaultToolRegistry();
        for (String toolName : whitelist) {
            Optional<ToolDefinition> def = parent.get(toolName);
            if (def.isPresent()) {
                filtered.register(def.get());
            } else {
                log.warn(
                        "Subagent tool whitelist references unknown tool '{}'; skipping (allowed:"
                                + " {})",
                        toolName,
                        parent.getAll().stream().map(ToolDefinition::name).toList());
            }
        }
        return filtered;
    }

    /** Internal value object — resolves either registered SubagentDefinition or ad-hoc params. */
    private record ResolvedSubagent(
            String name, String systemPrompt, List<String> tools, String model) {
        static ResolvedSubagent fromDefinition(SubagentDefinition def) {
            return new ResolvedSubagent(def.name(), def.systemPrompt(), def.tools(), def.model());
        }
    }

    private String extractText(Msg msg) {
        StringBuilder sb = new StringBuilder();
        for (Content content : msg.contents()) {
            if (content instanceof Content.TextContent tc) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(tc.text());
            }
        }
        return sb.isEmpty() ? msg.toString() : sb.toString();
    }
}
