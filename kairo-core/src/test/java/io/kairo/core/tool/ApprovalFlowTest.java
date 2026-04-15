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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.tool.*;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApprovalFlowTest {

    private DefaultToolRegistry registry;
    private DefaultToolExecutor executor;

    /** A PermissionGuard that allows everything (bypasses bash-level guard). */
    private static final PermissionGuard ALLOW_ALL =
            new PermissionGuard() {
                @Override
                public Mono<Boolean> checkPermission(String toolName, Map<String, Object> input) {
                    return Mono.just(true);
                }

                @Override
                public void addDangerousPattern(String pattern) {}
            };

    private UserApprovalHandler approvalHandler;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        executor = new DefaultToolExecutor(registry, ALLOW_ALL);
        approvalHandler = Mockito.mock(UserApprovalHandler.class);
    }

    private void registerTool(String name, ToolSideEffect sideEffect, ToolHandler handler) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "test tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        handler.getClass(),
                        null,
                        sideEffect);
        registry.register(def);
        registry.registerInstance(name, handler);
    }

    private ToolHandler echoHandler(String toolId) {
        return input -> new ToolResult(toolId, "result-" + toolId, false, Map.of());
    }

    @Test
    void allowedPermission_executesWithoutApproval() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));
        executor.setApprovalHandler(approvalHandler);

        var inv = new ToolInvocation("read_file", Map.of());
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("result-read_file", result.content());
                        })
                .verifyComplete();

        verifyNoInteractions(approvalHandler);
    }

    @Test
    void deniedPermission_rejectsImmediately() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        executor.setToolPermission("bash", ToolPermission.DENIED);
        executor.setApprovalHandler(approvalHandler);

        var inv = new ToolInvocation("bash", Map.of());
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("denied by permission policy"));
                        })
                .verifyComplete();

        verifyNoInteractions(approvalHandler);
    }

    @Test
    void askPermission_callsHandler_approved() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        executor.setApprovalHandler(approvalHandler);
        when(approvalHandler.requestApproval(any())).thenReturn(Mono.just(ApprovalResult.allow()));

        var inv = new ToolInvocation("bash", Map.of("command", "ls"));
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(
                        result -> {
                            assertFalse(result.isError());
                            assertEquals("result-bash", result.content());
                        })
                .verifyComplete();

        verify(approvalHandler).requestApproval(any(ToolCallRequest.class));
    }

    @Test
    void askPermission_callsHandler_denied() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        executor.setApprovalHandler(approvalHandler);
        when(approvalHandler.requestApproval(any()))
                .thenReturn(Mono.just(ApprovalResult.denied("too dangerous")));

        var inv = new ToolInvocation("bash", Map.of("command", "rm -rf /"));
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(result.content().contains("denied by user"));
                            assertTrue(result.content().contains("too dangerous"));
                        })
                .verifyComplete();
    }

    @Test
    void askPermission_noHandler_deniedBySafety() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        // No handler set — default is null

        var inv = new ToolInvocation("bash", Map.of());
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(
                        result -> {
                            assertTrue(result.isError());
                            assertTrue(
                                    result.content().contains("requires approval but no handler"));
                        })
                .verifyComplete();
    }

    @Test
    void toolSpecificPermissionOverridesDefault() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        // Default for SYSTEM_CHANGE is ASK, but override to ALLOWED
        executor.setToolPermission("bash", ToolPermission.ALLOWED);
        executor.setApprovalHandler(approvalHandler);

        var inv = new ToolInvocation("bash", Map.of());
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        verifyNoInteractions(approvalHandler);
    }

    @Test
    void categoryPermissionOverridesGlobalDefault() {
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));
        // Default for WRITE is ALLOWED, but override category to ASK
        executor.setDefaultPermission(ToolSideEffect.WRITE, ToolPermission.ASK);
        executor.setApprovalHandler(approvalHandler);
        when(approvalHandler.requestApproval(any())).thenReturn(Mono.just(ApprovalResult.allow()));

        var inv = new ToolInvocation("write_file", Map.of());
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        verify(approvalHandler).requestApproval(any());
    }

    @Test
    void systemChangeDefaultsToAsk() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        executor.setApprovalHandler(approvalHandler);
        when(approvalHandler.requestApproval(any())).thenReturn(Mono.just(ApprovalResult.allow()));

        var inv = new ToolInvocation("bash", Map.of());
        executor.executeSingle(inv).block();

        verify(approvalHandler).requestApproval(any());
    }

    @Test
    void readOnlyDefaultsToAllowed() {
        registerTool("read_file", ToolSideEffect.READ_ONLY, echoHandler("read_file"));
        executor.setApprovalHandler(approvalHandler);

        var inv = new ToolInvocation("read_file", Map.of());
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        verifyNoInteractions(approvalHandler);
    }

    @Test
    void writeDefaultsToAllowed() {
        registerTool("write_file", ToolSideEffect.WRITE, echoHandler("write_file"));
        executor.setApprovalHandler(approvalHandler);

        var inv = new ToolInvocation("write_file", Map.of());
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        verifyNoInteractions(approvalHandler);
    }

    @Test
    void permissionResolutionOrder() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        executor.setApprovalHandler(approvalHandler);

        // Set category to DENIED
        executor.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.DENIED);
        // Set tool-specific to ALLOWED — should override category
        executor.setToolPermission("bash", ToolPermission.ALLOWED);

        var inv = new ToolInvocation("bash", Map.of());
        StepVerifier.create(executor.executeSingle(inv))
                .assertNext(result -> assertFalse(result.isError()))
                .verifyComplete();

        verifyNoInteractions(approvalHandler);
    }

    @Test
    void approvalHandlerReceivesCorrectRequest() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE, echoHandler("bash"));
        executor.setApprovalHandler(approvalHandler);

        var captor = ArgumentCaptor.forClass(ToolCallRequest.class);
        when(approvalHandler.requestApproval(captor.capture()))
                .thenReturn(Mono.just(ApprovalResult.allow()));

        var args = Map.<String, Object>of("command", "echo hello");
        var inv = new ToolInvocation("bash", args);
        executor.executeSingle(inv).block();

        ToolCallRequest captured = captor.getValue();
        assertEquals("bash", captured.toolName());
        assertEquals(args, captured.args());
        assertEquals(ToolSideEffect.SYSTEM_CHANGE, captured.sideEffect());
    }
}
