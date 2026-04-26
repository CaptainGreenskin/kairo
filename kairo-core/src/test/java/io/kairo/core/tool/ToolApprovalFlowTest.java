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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.tool.ApprovalResult;
import io.kairo.api.tool.PermissionGuard;
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

class ToolApprovalFlowTest {

    private ToolPermissionResolver resolver;
    private ToolExecutor executor;
    private ToolApprovalFlow approvalFlow;

    private static final ToolInvocation INVOCATION =
            new ToolInvocation("my-tool", Map.of("key", "value"));

    @BeforeEach
    void setUp() {
        executor = mock(ToolExecutor.class);
        resolver =
                new ToolPermissionResolver(mock(PermissionGuard.class), new DefaultToolRegistry());
        approvalFlow = new ToolApprovalFlow(resolver, executor);
    }

    @Test
    void approveIfNeeded_allowedPermission_executesTool() {
        resolver.setToolPermission("my-tool", ToolPermission.ALLOWED);
        var expected = new ToolResult("id", "output", false, Map.of());
        when(executor.execute(anyString(), any())).thenReturn(Mono.just(expected));

        StepVerifier.create(approvalFlow.approveIfNeeded(INVOCATION))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void approveIfNeeded_deniedPermission_returnsErrorResult() {
        resolver.setToolPermission("my-tool", ToolPermission.DENIED);

        StepVerifier.create(approvalFlow.approveIfNeeded(INVOCATION))
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("denied");
                        })
                .verifyComplete();
    }

    @Test
    void approveIfNeeded_askWithNoHandler_returnsErrorResult() {
        // unknown tool defaults to SYSTEM_CHANGE → ASK; no handler set
        StepVerifier.create(approvalFlow.approveIfNeeded(INVOCATION))
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("no handler configured");
                        })
                .verifyComplete();
    }

    @Test
    void approveIfNeeded_userApproves_executesTool() {
        var handler = mock(UserApprovalHandler.class);
        approvalFlow.setApprovalHandler(handler);
        when(handler.requestApproval(any())).thenReturn(Mono.just(ApprovalResult.allow()));
        var expected = new ToolResult("id", "result", false, Map.of());
        when(executor.execute(anyString(), any())).thenReturn(Mono.just(expected));

        StepVerifier.create(approvalFlow.approveIfNeeded(INVOCATION))
                .expectNext(expected)
                .verifyComplete();
    }

    @Test
    void approveIfNeeded_userDenies_returnsErrorResultWithReason() {
        var handler = mock(UserApprovalHandler.class);
        approvalFlow.setApprovalHandler(handler);
        when(handler.requestApproval(any()))
                .thenReturn(Mono.just(ApprovalResult.denied("user said no")));

        StepVerifier.create(approvalFlow.approveIfNeeded(INVOCATION))
                .assertNext(
                        result -> {
                            assertThat(result.isError()).isTrue();
                            assertThat(result.content()).contains("user said no");
                        })
                .verifyComplete();
    }
}
