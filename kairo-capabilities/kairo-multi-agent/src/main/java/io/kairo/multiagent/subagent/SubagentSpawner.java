package io.kairo.multiagent.subagent;

import io.kairo.api.agent.SubagentDefinition;
import javax.annotation.Nullable;
import reactor.core.publisher.Mono;

/**
 * SPI for spawning subagent sessions. Implementations decide how the child inherits parent state
 * (model provider, hooks, tools, workspace) and how results are returned.
 *
 * @since 1.3
 */
public interface SubagentSpawner {

    /**
     * Spawn a subagent and run it to completion synchronously.
     *
     * @param request the spawn request containing all parameters
     * @return the child agent's final response
     */
    Mono<SubagentResult> spawn(SubagentRequest request);

    /**
     * Spawn a subagent in the background (async). Returns immediately with a handle.
     *
     * @param request the spawn request
     * @return a handle to track the background agent
     */
    Mono<SubagentHandle> spawnAsync(SubagentRequest request);

    /** Request to spawn a subagent. */
    record SubagentRequest(
            String taskId,
            String prompt,
            String description,
            @Nullable SubagentType type,
            @Nullable SubagentDefinition customDefinition,
            @Nullable String modelOverride,
            @Nullable String name,
            @Nullable String parentContext,
            String workingDir,
            int maxDepth) {

        public SubagentRequest {
            if (taskId == null || taskId.isBlank())
                throw new IllegalArgumentException("taskId required");
            if (prompt == null || prompt.isBlank())
                throw new IllegalArgumentException("prompt required");
            if (workingDir == null) throw new IllegalArgumentException("workingDir required");
        }
    }

    /** Result of a completed subagent execution. */
    record SubagentResult(
            String taskId,
            String response,
            int toolCallCount,
            long durationMs,
            @Nullable Throwable error) {

        public boolean isSuccess() {
            return error == null;
        }
    }

    /** Handle for a background subagent. */
    record SubagentHandle(String taskId, String name, SubagentType type) {}
}
