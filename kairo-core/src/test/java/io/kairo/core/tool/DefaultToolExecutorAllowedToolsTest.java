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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.kairo.api.tool.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultToolExecutorAllowedToolsTest {

    private DefaultToolRegistry registry;
    private PermissionGuard permissionGuard;
    private DefaultToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        permissionGuard = mock(PermissionGuard.class);
        when(permissionGuard.checkPermission(anyString(), any())).thenReturn(Mono.just(true));
        when(permissionGuard.checkPermissionDetail(anyString(), any(Map.class)))
                .thenReturn(Mono.just(PermissionDecision.allow()));
        when(permissionGuard.checkPermissionDetail(
                        anyString(), any(ToolCategory.class), any(Map.class)))
                .thenReturn(Mono.just(PermissionDecision.allow()));
        executor = new DefaultToolExecutor(registry, permissionGuard);

        // Register mock tools
        registerMockTool("Read", ToolSideEffect.READ_ONLY);
        registerMockTool("Grep", ToolSideEffect.READ_ONLY);
        registerMockTool("Write", ToolSideEffect.WRITE);
        registerMockTool("skill_load", ToolSideEffect.READ_ONLY);
        registerMockTool("skill_list", ToolSideEffect.READ_ONLY);
    }

    private void registerMockTool(String name, ToolSideEffect sideEffect) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "desc",
                        ToolCategory.FILE_AND_CODE,
                        null,
                        ToolHandler.class,
                        null,
                        sideEffect);
        registry.register(def);
        ToolHandler handler = mock(ToolHandler.class);
        try {
            when(handler.execute(any())).thenReturn(new ToolResult(name, "ok", false, Map.of()));
            when(handler.execute(any(), any()))
                    .thenReturn(new ToolResult(name, "ok", false, Map.of()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        registry.registerInstance(name, handler);
    }

    @Test
    void toolBlockedWhenNotInAllowedList() {
        executor.setAllowedTools(Set.of("Read", "Grep"));

        ToolResult result = executor.execute("Write", Map.of()).block();
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.content().contains("not allowed"));
    }

    @Test
    void toolAllowedWhenInList() {
        executor.setAllowedTools(Set.of("Read", "Grep"));

        ToolResult result = executor.execute("Read", Map.of()).block();
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("ok", result.content());
    }

    @Test
    void skillLoadAlwaysAllowed() {
        executor.setAllowedTools(Set.of("Read"));

        ToolResult result = executor.execute("skill_load", Map.of()).block();
        assertNotNull(result);
        assertFalse(result.isError());
    }

    @Test
    void skillListAlwaysAllowed() {
        executor.setAllowedTools(Set.of("Read"));

        ToolResult result = executor.execute("skill_list", Map.of()).block();
        assertNotNull(result);
        assertFalse(result.isError());
    }

    @Test
    void clearAllowedToolsRemovesRestriction() {
        executor.setAllowedTools(Set.of("Read"));
        executor.clearAllowedTools();

        ToolResult result = executor.execute("Write", Map.of()).block();
        assertNotNull(result);
        assertFalse(result.isError());
    }

    @Test
    void nullConstraintsMeansNoRestriction() {
        // Default state — no setAllowedTools called
        ToolResult result = executor.execute("Write", Map.of()).block();
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals("ok", result.content());
    }
}
