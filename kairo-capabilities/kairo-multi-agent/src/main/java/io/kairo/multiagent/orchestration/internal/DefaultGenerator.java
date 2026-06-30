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
package io.kairo.multiagent.orchestration.internal;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.TeamResult.StepOutcome;
import io.kairo.api.team.TeamStep;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.agent.DefaultReActAgent;
import io.kairo.core.agent.ToolCallSink;
import io.kairo.multiagent.orchestration.ExpertMemoryEntry;
import io.kairo.multiagent.orchestration.ExpertMemoryStore;
import io.kairo.multiagent.orchestration.tool.RoleScopedToolExecutor;
import io.kairo.multiagent.subagent.ExpertProfile;
import io.kairo.multiagent.subagent.ExpertRoleRegistry;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Dispatches a single {@link TeamStep} to its role-bound {@link Agent} and returns the generated
 * artifact text.
 *
 * <p>The generator is stateless; the coordinator is responsible for tracking attempt counts and
 * prior verdicts. Revise loops are modelled by re-invoking {@link #generate} with an incremented
 * {@code attemptNumber} and the cumulative list of prior verdicts so the agent can react to
 * feedback.
 *
 * @since v0.10 (Experimental)
 */
public final class DefaultGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultGenerator.class);

    private final ExpertRoleRegistry roleRegistry;
    private final SkillRegistry skillRegistry; // nullable — works without skills
    private final ExpertMemoryStore memoryStore; // nullable — self-evolution off when null

    /** Create a generator without skill injection support. */
    public DefaultGenerator() {
        this(null, null, null);
    }

    /**
     * Create a generator with optional skill injection.
     *
     * @param roleRegistry the expert role registry (nullable)
     * @param skillRegistry the skill registry (nullable — system works without skills)
     */
    public DefaultGenerator(ExpertRoleRegistry roleRegistry, SkillRegistry skillRegistry) {
        this(roleRegistry, skillRegistry, null);
    }

    /**
     * Create a generator with skill injection + cross-task lesson recall (self-evolution).
     *
     * @param roleRegistry the expert role registry (nullable)
     * @param skillRegistry the skill registry (nullable)
     * @param memoryStore the expert memory store for prior-lesson recall (nullable — off when null)
     */
    public DefaultGenerator(
            ExpertRoleRegistry roleRegistry,
            SkillRegistry skillRegistry,
            ExpertMemoryStore memoryStore) {
        this.roleRegistry = roleRegistry;
        this.skillRegistry = skillRegistry;
        this.memoryStore = memoryStore;
    }

    /** Produce an artifact for the given step using the role-bound agent. */
    public Mono<String> generate(
            TeamStep step,
            Map<String, Agent> roleBindings,
            String goal,
            int attemptNumber,
            List<EvaluationVerdict> priorVerdicts) {
        return generateWithModelOverride(
                step, roleBindings, goal, attemptNumber, priorVerdicts, null, null, List.of());
    }

    /**
     * Produce an artifact for the given step, streaming the worker agent's individual tool calls to
     * {@code toolCallSink} (nullable). See {@link ToolCallSink}.
     */
    public Mono<String> generate(
            TeamStep step,
            Map<String, Agent> roleBindings,
            String goal,
            int attemptNumber,
            List<EvaluationVerdict> priorVerdicts,
            @Nullable ToolCallSink toolCallSink) {
        return generate(
                step, roleBindings, goal, attemptNumber, priorVerdicts, toolCallSink, List.of());
    }

    /**
     * Produce an artifact, streaming tool calls and injecting the outputs of upstream steps this
     * step depends on into the prompt (see {@link StepOutcome}).
     */
    public Mono<String> generate(
            TeamStep step,
            Map<String, Agent> roleBindings,
            String goal,
            int attemptNumber,
            List<EvaluationVerdict> priorVerdicts,
            @Nullable ToolCallSink toolCallSink,
            List<StepOutcome> upstreamOutcomes) {
        return generateWithModelOverride(
                step,
                roleBindings,
                goal,
                attemptNumber,
                priorVerdicts,
                null,
                toolCallSink,
                upstreamOutcomes);
    }

    /**
     * Produce an artifact for the given step, optionally overriding the model used by the agent.
     *
     * <p>When {@code modelOverride} is non-null, the message metadata carries a {@code
     * kairo.modelOverride} hint that downstream agent implementations may honour to escalate to a
     * senior model.
     *
     * @param modelOverride nullable model identifier for escalation; {@code null} uses the default
     */
    public Mono<String> generateWithModelOverride(
            TeamStep step,
            Map<String, Agent> roleBindings,
            String goal,
            int attemptNumber,
            List<EvaluationVerdict> priorVerdicts,
            @Nullable String modelOverride) {
        return generateWithModelOverride(
                step,
                roleBindings,
                goal,
                attemptNumber,
                priorVerdicts,
                modelOverride,
                null,
                List.of());
    }

    /**
     * Produce an artifact, optionally overriding the model and streaming the worker's tool calls.
     *
     * <p>When {@code toolCallSink} is non-null it is published into the Reactor Context under
     * {@link ToolCallSink#CONTEXT_KEY}, so the worker agent's {@code ToolPhase} forwards each tool
     * call (the real read/edit/bash, not a single opaque {@code agent.call}) to the sink.
     */
    public Mono<String> generateWithModelOverride(
            TeamStep step,
            Map<String, Agent> roleBindings,
            String goal,
            int attemptNumber,
            List<EvaluationVerdict> priorVerdicts,
            @Nullable String modelOverride,
            @Nullable ToolCallSink toolCallSink,
            List<StepOutcome> upstreamOutcomes) {
        Objects.requireNonNull(step, "step must not be null");
        Objects.requireNonNull(roleBindings, "roleBindings must not be null");
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(priorVerdicts, "priorVerdicts must not be null");

        Agent agent = roleBindings.get(step.assignedRole().roleId());
        if (agent == null) {
            return Mono.error(
                    new IllegalStateException(
                            "No agent bound to role '"
                                    + step.assignedRole().roleId()
                                    + "' for step '"
                                    + step.stepId()
                                    + "'"));
        }

        String prompt = buildPrompt(step, goal, attemptNumber, priorVerdicts, upstreamOutcomes);
        Msg input;
        if (modelOverride != null) {
            input =
                    Msg.builder()
                            .role(MsgRole.USER)
                            .addContent(new io.kairo.api.message.Content.TextContent(prompt))
                            .metadata("kairo.modelOverride", modelOverride)
                            .build();
        } else {
            input = Msg.of(MsgRole.USER, prompt);
        }

        // ── Per-step role-scoped tool restriction ─────────────────────────────
        List<String> allowedTools = step.assignedRole().allowedTools();
        RoleScopedToolExecutor scopedExecutor = null;
        ToolExecutor originalExecutor = null;
        if (!allowedTools.isEmpty()) {
            originalExecutor = extractToolExecutor(agent);
            if (originalExecutor != null) {
                scopedExecutor =
                        new RoleScopedToolExecutor(
                                originalExecutor, allowedTools, step.assignedRole().roleId());
                injectToolExecutor(agent, scopedExecutor);
                log.debug(
                        "Step '{}' role '{}': tool restriction active (allowed: {})",
                        step.stepId(),
                        step.assignedRole().roleId(),
                        allowedTools);
            }
        }

        final RoleScopedToolExecutor capturedScopedExecutor = scopedExecutor;
        final ToolExecutor capturedOriginalExecutor = originalExecutor;

        Mono<String> call = agent.call(input).map(Msg::text);
        if (toolCallSink != null) {
            call = call.contextWrite(ctx -> ctx.put(ToolCallSink.CONTEXT_KEY, toolCallSink));
        }

        // Restore original executor after step completes (success or error)
        if (capturedOriginalExecutor != null) {
            call =
                    call.doFinally(
                            signal -> {
                                injectToolExecutor(agent, capturedOriginalExecutor);
                                if (capturedScopedExecutor != null) {
                                    log.info(
                                            "Step '{}' role '{}' completed with {} tool rejections",
                                            step.stepId(),
                                            step.assignedRole().roleId(),
                                            capturedScopedExecutor.getRejectionCount());
                                }
                            });
        }
        return call;
    }

    /** Max chars of an upstream artifact injected into a downstream step's prompt. */
    private static final int MAX_UPSTREAM_CHARS = 6_000;

    private String buildPrompt(
            TeamStep step,
            String goal,
            int attemptNumber,
            List<EvaluationVerdict> priorVerdicts,
            List<StepOutcome> upstreamOutcomes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Goal] ").append(goal).append('\n');
        sb.append("[Step ")
                .append(step.stepIndex() + 1)
                .append("] ")
                .append(step.description())
                .append('\n');
        sb.append("[Role] ")
                .append(step.assignedRole().roleName())
                .append("\n[Instructions]\n")
                .append(step.assignedRole().instructions())
                .append('\n');

        // Inject mounted skill content if available
        appendMountedSkills(sb, step.assignedRole().roleId());

        // Inject prior lessons (self-evolution): recall top-N lessons for this role from
        // cross-task memory, so the expert benefits from past experience instead of starting cold.
        appendPriorLessons(sb, step.assignedRole().roleId());

        // Feed the outputs of the steps this one depends on, so e.g. the coder sees the
        // architect's design instead of having to rediscover it from the filesystem.
        if (upstreamOutcomes != null && !upstreamOutcomes.isEmpty()) {
            sb.append("\n[Upstream outputs] (results of steps you depend on)\n");
            for (StepOutcome o : upstreamOutcomes) {
                String out = o.output() == null ? "" : o.output();
                if (out.length() > MAX_UPSTREAM_CHARS) {
                    out = out.substring(0, MAX_UPSTREAM_CHARS) + "\n…(truncated)";
                }
                sb.append("\n## From ").append(o.stepId()).append(":\n").append(out).append('\n');
            }
        }

        if (attemptNumber > 1 && !priorVerdicts.isEmpty()) {
            sb.append("[Revision attempt ").append(attemptNumber).append("]\n");
            EvaluationVerdict last = priorVerdicts.get(priorVerdicts.size() - 1);
            if (!last.feedback().isBlank()) {
                sb.append("Previous feedback: ").append(last.feedback()).append('\n');
            }
            if (!last.suggestions().isEmpty()) {
                sb.append("Suggestions:\n");
                for (String s : last.suggestions()) {
                    sb.append(" - ").append(s).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /**
     * Appends mounted skill content from the expert profile into the prompt. Silently skips if
     * registries are unavailable or a skill ID does not resolve.
     */
    private void appendMountedSkills(StringBuilder sb, String roleId) {
        if (roleRegistry == null || skillRegistry == null) {
            return;
        }
        Optional<ExpertProfile> profileOpt = roleRegistry.resolve(roleId);
        if (profileOpt.isEmpty()) {
            return;
        }
        ExpertProfile profile = profileOpt.get();
        if (profile.mountedSkills().isEmpty()) {
            return;
        }
        for (String skillId : profile.mountedSkills()) {
            Optional<SkillDefinition> skillOpt = skillRegistry.get(skillId);
            if (skillOpt.isPresent()) {
                SkillDefinition skill = skillOpt.get();
                if (skill.hasInstructions()) {
                    sb.append("\n## Skill: ")
                            .append(skill.name())
                            .append('\n')
                            .append(skill.instructions())
                            .append('\n');
                }
            }
        }
    }

    /**
     * Recall the top prior lessons for this role from cross-task memory and inject them so the
     * expert starts from accumulated experience rather than cold. No-op when no memory store is
     * configured or the role has no recorded lessons.
     */
    private void appendPriorLessons(StringBuilder sb, String roleId) {
        if (memoryStore == null) {
            return;
        }
        try {
            // Recall is reactive (file I/O); block here since buildPrompt is a synchronous prompt
            // assembly on a boundedElastic-backed execution thread.
            List<ExpertMemoryEntry> lessons =
                    memoryStore.recall(roleId, roleId, 5).collectList().block();
            if (lessons == null || lessons.isEmpty()) {
                return;
            }
            sb.append(
                    "\n[Prior Lessons] (cross-task experience for your role — apply where relevant)\n");
            for (ExpertMemoryEntry e : lessons) {
                sb.append("- ").append(e.lesson()).append('\n');
            }
            // INFO so self-evolution is observable end-to-end: this is the only proof that a
            // recalled lesson actually reached a worker prompt. Without it, the L1-style "wired but
            // silently no-op" failure is invisible (recall returning nothing looks identical to a
            // successful injection of nothing).
            log.info(
                    "Injected {} prior lesson(s) into worker prompt for role {}",
                    lessons.size(),
                    roleId);
        } catch (Exception e) {
            log.debug("Prior-lesson recall failed for role {}: {}", roleId, e.getMessage());
        }
    }

    // ── Reflective tool executor access ────────────────────────────────────────

    private static final Field TOOL_EXECUTOR_FIELD;

    static {
        Field f = null;
        try {
            f = DefaultReActAgent.class.getDeclaredField("toolExecutor");
            f.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            // Silently degrade — tool scoping will be unavailable
            LoggerFactory.getLogger(DefaultGenerator.class)
                    .warn(
                            "Cannot access toolExecutor field; role-scoped tool restriction disabled",
                            e);
        }
        TOOL_EXECUTOR_FIELD = f;
    }

    @Nullable
    private static ToolExecutor extractToolExecutor(Agent agent) {
        if (TOOL_EXECUTOR_FIELD == null || !(agent instanceof DefaultReActAgent)) {
            return null;
        }
        try {
            return (ToolExecutor) TOOL_EXECUTOR_FIELD.get(agent);
        } catch (IllegalAccessException e) {
            log.warn("Failed to extract toolExecutor from agent '{}'", agent.name(), e);
            return null;
        }
    }

    private static void injectToolExecutor(Agent agent, ToolExecutor executor) {
        if (TOOL_EXECUTOR_FIELD == null || !(agent instanceof DefaultReActAgent)) {
            return;
        }
        try {
            TOOL_EXECUTOR_FIELD.set(agent, executor);
        } catch (IllegalAccessException e) {
            log.warn("Failed to inject toolExecutor into agent '{}'", agent.name(), e);
        }
    }
}
