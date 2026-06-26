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
package io.kairo.multiagent.orchestration.tool;

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolEvent;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.tool.UserApprovalHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorates a {@link ToolExecutor} with a team-scoped {@link ReadFileCache}.
 *
 * <p>Intercepts {@code read_file} calls: returns cached content on hit, stores result on miss.
 * After any mutation tool ({@code bash}, {@code write_file}, {@code edit_file}) executes, the
 * entire cache is invalidated per the full-clear-on-mutation policy.
 *
 * <p>The decoration order is:
 *
 * <pre>
 * Per-step: RoleScopedToolExecutor → Per-team: CachingToolExecutor → Base: DefaultToolExecutor
 * </pre>
 *
 * @since v0.10
 */
public class CachingToolExecutor implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(CachingToolExecutor.class);

    private static final String READ_FILE_TOOL = "read_file";
    private static final Set<String> MUTATION_TOOLS = Set.of("bash", "write_file", "edit_file");

    private final ToolExecutor delegate;
    private final ReadFileCache cache;

    /**
     * @param delegate the underlying executor to delegate to; must not be null
     * @param cache the shared team-scoped cache; must not be null
     */
    public CachingToolExecutor(ToolExecutor delegate, ReadFileCache cache) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
        if (READ_FILE_TOOL.equals(toolName)) {
            String path = extractPath(input);
            if (path != null) {
                Optional<String> cached = cache.get(path);
                if (cached.isPresent()) {
                    log.debug("ReadFileCache hit for '{}'", path);
                    return Mono.just(ToolResult.success("", cached.get()));
                }
            }
            return delegate.execute(toolName, input)
                    .doOnNext(result -> cacheIfSuccess(path, result));
        }

        if (isMutation(toolName)) {
            return delegate.execute(toolName, input)
                    .doOnNext(result -> cache.invalidateAll(toolName));
        }

        return delegate.execute(toolName, input);
    }

    @Override
    public Mono<ToolResult> execute(String toolName, Map<String, Object> input, Duration timeout) {
        if (READ_FILE_TOOL.equals(toolName)) {
            String path = extractPath(input);
            if (path != null) {
                Optional<String> cached = cache.get(path);
                if (cached.isPresent()) {
                    log.debug("ReadFileCache hit for '{}'", path);
                    return Mono.just(ToolResult.success("", cached.get()));
                }
            }
            return delegate.execute(toolName, input, timeout)
                    .doOnNext(result -> cacheIfSuccess(path, result));
        }

        if (isMutation(toolName)) {
            return delegate.execute(toolName, input, timeout)
                    .doOnNext(result -> cache.invalidateAll(toolName));
        }

        return delegate.execute(toolName, input, timeout);
    }

    @Override
    public Flux<ToolResult> executeParallel(List<ToolInvocation> invocations) {
        // Delegate to the base executor for parallel execution, then apply cache logic per-result.
        // For simplicity in the parallel path, we attempt cache hits before delegating and
        // handle results after completion.
        return Flux.fromIterable(invocations).flatMap(inv -> executeSingle(inv));
    }

    @Override
    public Mono<ToolResult> executeSingle(ToolInvocation invocation) {
        String toolName = invocation.toolName();

        if (READ_FILE_TOOL.equals(toolName)) {
            String path = extractPath(invocation.input());
            if (path != null) {
                Optional<String> cached = cache.get(path);
                if (cached.isPresent()) {
                    log.debug("ReadFileCache hit for '{}'", path);
                    String toolCallId =
                            invocation.toolCallId() != null ? invocation.toolCallId() : "";
                    return Mono.just(ToolResult.success(toolCallId, cached.get()));
                }
            }
            return delegate.executeSingle(invocation)
                    .doOnNext(result -> cacheIfSuccess(path, result));
        }

        if (isMutation(toolName)) {
            return delegate.executeSingle(invocation)
                    .doOnNext(result -> cache.invalidateAll(toolName));
        }

        return delegate.executeSingle(invocation);
    }

    // ── Delegation of non-execution methods ──────────────────────────────────

    @Override
    public void setAllowedTools(Set<String> tools) {
        delegate.setAllowedTools(tools);
    }

    @Override
    public void clearAllowedTools() {
        delegate.clearAllowedTools();
    }

    @Override
    public void registerToolInstance(String toolName, Object instance) {
        delegate.registerToolInstance(toolName, instance);
    }

    @Override
    public void setToolMetadata(String toolName, Map<String, Object> metadata) {
        delegate.setToolMetadata(toolName, metadata);
    }

    @Override
    public boolean supportsStreaming() {
        return delegate.supportsStreaming();
    }

    @Override
    public ToolSideEffect resolveSideEffect(String toolName) {
        return delegate.resolveSideEffect(toolName);
    }

    @Override
    public void setApprovalHandler(UserApprovalHandler approvalHandler) {
        delegate.setApprovalHandler(approvalHandler);
    }

    @Override
    public void resetCircuitBreaker() {
        delegate.resetCircuitBreaker();
    }

    @Override
    public void resetCircuitBreaker(String toolName) {
        delegate.resetCircuitBreaker(toolName);
    }

    @Override
    public Flux<ToolEvent> executeStreaming(
            Object tool, Map<String, Object> args, ToolContext ctx) {
        return delegate.executeStreaming(tool, args, ctx);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private boolean isMutation(String toolName) {
        return MUTATION_TOOLS.contains(toolName);
    }

    private String extractPath(Map<String, Object> input) {
        Object path = input.get("path");
        if (path == null) {
            path = input.get("file_path");
        }
        return path instanceof String s ? s : null;
    }

    private void cacheIfSuccess(String path, ToolResult result) {
        if (path != null && !result.isError()) {
            cache.put(path, result.content());
        }
    }
}
