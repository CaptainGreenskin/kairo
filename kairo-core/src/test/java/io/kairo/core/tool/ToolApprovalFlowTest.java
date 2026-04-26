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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kairo.api.tool.ApprovalResult;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolPermission;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.UserApprovalHandler;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for {@link ToolApprovalFlow}. */
class ToolApprovalFlowTest {

    private DefaultPermissionGuard guard;
    private DefaultToolRegistry registry;
    private ToolPermissionResolver permissionResolver;
    private ToolExecutor executor;
    private ToolApprovalFlow flow;

    private static final String TOOL_NAME = "bash";
    private static final Map<String, Object> INPUT = Map.of("command", "echo hello");
    private static final ToolInvocation INVOCATION = new ToolInvocation(TOOL_NAME, INPUT);
    private static final ToolResult SUCCESS_RESULT =
            new ToolResult("use-1", "hello", false, Map.of());

    @BeforeEach
    void setUp() {
        guard = new DefaultPermissionGuard();
        registry = new DefaultToolRegistry();
        permissionResolver = new ToolPermissionResolver(guard, registry);
        executor = mock(ToolExecutor.class);
        flow = new ToolApprovalFlow(permissionResolver, executor);
    }

    // ===== ALLOWED =====

    @Test
    void approveIfNeeded_allowed_executesDirectly() {
        permissionResolver.setToolPermission(TOOL_NAME, ToolPermission.ALLOWED);
        when(executor.execute(TOOL_NAME, INPUT)).thenReturn(Mono.just(SUCCESS_RESULT));

        StepVerifier.create(flow.approveIfNeeded(INVOCATION))
                .assertNext(r -> assertThat(r.content()).isEqualTo("hello"))
                .verifyComplete();

        verify(executor).execute(TOOL_NAME, INPUT);
    }

    // ===== DENIED =====

    @Test
    void approveIfNeeded_denied_returnsErrorWithoutExecuting() {
        permissionResolver.setToolPermission(TOOL_NAME, ToolPermission.DENIED);

        StepVerifier.create(flow.approveIfNeeded(INVOCATION))
                .assertNext(
                        r -> {
                            assertThat(r.isError()).isTrue();
                            assertThat(r.content()).contains("denied");
                        })
                .verifyComplete();

        verify(executor, never()).execute(any(), any());
    }

    // ===== ASK — no handler =====

    @Test
    void approveIfNeeded_ask_noHandler_returnsError() {
        permissionResolver.setToolPermission(TOOL_NAME, ToolPermission.ASK);

        // No approvalHandler set

        StepVerifier.create(flow.approveIfNeeded(INVOCATION))
                .assertNext(
                        r -> {
                            assertThat(r.isError()).isTrue();
                            assertThat(r.content()).contains("no handler");
                        })
                .verifyComplete();

        verify(executor, never()).execute(any(), any());
    }

    // ===== ASK — handler approves =====

    @Test
    void approveIfNeeded_ask_handlerApproves_executesTool() {
        permissionResolver.setToolPermission(TOOL_NAME, ToolPermission.ASK);
        when(executor.execute(TOOL_NAME, INPUT)).thenReturn(Mono.just(SUCCESS_RESULT));

        UserApprovalHandler handler = mock(UserApprovalHandler.class);
        when(handler.requestApproval(any())).thenReturn(Mono.just(ApprovalResult.allow()));
        flow.setApprovalHandler(handler);

        StepVerifier.create(flow.approveIfNeeded(INVOCATION))
                .assertNext(r -> assertThat(r.isError()).isFalse())
                .verifyComplete();

        verify(executor).execute(TOOL_NAME, INPUT);
    }

    // ===== ASK — handler denies =====

    @Test
    void approveIfNeeded_ask_handlerDenies_returnsErrorWithReason() {
        permissionResolver.setToolPermission(TOOL_NAME, ToolPermission.ASK);

        UserApprovalHandler handler = mock(UserApprovalHandler.class);
        when(handler.requestApproval(any()))
                .thenReturn(Mono.just(ApprovalResult.denied("user said no")));
        flow.setApprovalHandler(handler);

        StepVerifier.create(flow.approveIfNeeded(INVOCATION))
                .assertNext(
                        r -> {
                            assertThat(r.isError()).isTrue();
                            assertThat(r.content()).contains("user said no");
                        })
                .verifyComplete();

        verify(executor, never()).execute(any(), any());
    }

    // ===== ASK — handler called with correct tool name =====

    @Test
    void approveIfNeeded_ask_handlerReceivesCorrectToolName() {
        permissionResolver.setToolPermission(TOOL_NAME, ToolPermission.ASK);
        when(executor.execute(TOOL_NAME, INPUT)).thenReturn(Mono.just(SUCCESS_RESULT));

        UserApprovalHandler handler = mock(UserApprovalHandler.class);
        when(handler.requestApproval(any()))
                .thenAnswer(
                        inv -> {
                            io.kairo.api.tool.ToolCallRequest req = inv.getArgument(0);
                            assertThat(req.toolName()).isEqualTo(TOOL_NAME);
                            return Mono.just(ApprovalResult.allow());
                        });
        flow.setApprovalHandler(handler);

        StepVerifier.create(flow.approveIfNeeded(INVOCATION)).assertNext(r -> {}).verifyComplete();
    }
}
