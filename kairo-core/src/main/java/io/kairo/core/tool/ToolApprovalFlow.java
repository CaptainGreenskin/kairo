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
package io.kairo.core.tool;

import io.kairo.api.tool.*;
import reactor.core.publisher.Mono;

/**
 * Manages the user-approval workflow for tool invocations.
 *
 * <p>Delegates to {@link ToolPermissionResolver} for permission checks and routes to the configured
 * {@link UserApprovalHandler} when the resolved permission is {@link ToolPermission#ASK}.
 *
 * <p>Extracted from {@link DefaultToolExecutor} pipeline.
 */
public final class ToolApprovalFlow {

    private final ToolPermissionResolver permissionResolver;
    private final ToolExecutor executor;
    private volatile UserApprovalHandler approvalHandler;

    /**
     * Create a new approval flow.
     *
     * @param permissionResolver the permission resolver
     * @param executor the executor to delegate actual execution to
     */
    public ToolApprovalFlow(ToolPermissionResolver permissionResolver, ToolExecutor executor) {
        this.permissionResolver = permissionResolver;
        this.executor = executor;
    }

    /**
     * Set the approval handler for human-in-the-loop confirmation.
     *
     * @param handler the approval handler, or null to disable approval flow
     */
    public void setApprovalHandler(UserApprovalHandler handler) {
        this.approvalHandler = handler;
    }

    /**
     * Execute a tool invocation with approval check.
     *
     * <p>Checks the resolved permission for the tool and either executes directly, denies, or
     * requests user approval via the configured {@link UserApprovalHandler}.
     *
     * @param invocation the tool invocation
     * @return a Mono emitting the tool result
     */
    public Mono<ToolResult> approveIfNeeded(ToolInvocation invocation) {
        var sideEffect = permissionResolver.resolveSideEffect(invocation.toolName());
        var permission = permissionResolver.resolvePermission(invocation.toolName(), sideEffect);

        return switch (permission) {
            case ALLOWED -> executor.execute(invocation.toolName(), invocation.input());
            case DENIED ->
                    Mono.just(
                            ToolResultSanitizer.errorResult(
                                    invocation.toolName(),
                                    "Tool '"
                                            + invocation.toolName()
                                            + "' is denied by permission policy"));
            case ASK -> {
                if (approvalHandler == null) {
                    yield Mono.just(
                            ToolResultSanitizer.errorResult(
                                    invocation.toolName(),
                                    "Tool '"
                                            + invocation.toolName()
                                            + "' requires approval but no handler configured"));
                }
                var request =
                        new ToolCallRequest(invocation.toolName(), invocation.input(), sideEffect);
                yield approvalHandler
                        .requestApproval(request)
                        .flatMap(
                                result -> {
                                    if (result.approved()) {
                                        return executor.execute(
                                                invocation.toolName(), invocation.input());
                                    }
                                    return Mono.just(
                                            ToolResultSanitizer.errorResult(
                                                    invocation.toolName(),
                                                    "Tool '"
                                                            + invocation.toolName()
                                                            + "' denied by user: "
                                                            + result.reason()));
                                });
            }
        };
    }
}
