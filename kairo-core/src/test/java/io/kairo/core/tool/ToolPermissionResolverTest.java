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

import io.kairo.api.exception.PlanModeViolationException;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolPermission;
import io.kairo.api.tool.ToolSideEffect;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolPermissionResolverTest {

    private DefaultToolRegistry registry;
    private DefaultPermissionGuard guard;
    private ToolPermissionResolver resolver;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        guard = new DefaultPermissionGuard();
        resolver = new ToolPermissionResolver(guard, registry);
    }

    private void registerTool(String name, ToolSideEffect sideEffect) {
        ToolDefinition def =
                new ToolDefinition(
                        name,
                        "test tool " + name,
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        ToolHandler.class,
                        null,
                        sideEffect);
        registry.register(def);
    }

    // ===== Active tool constraints =====

    @Test
    void noConstraints_allToolsAllowed() {
        // activeToolConstraints is null by default (no restriction)
        assertNull(resolver.getActiveToolConstraints());
        assertTrue(resolver.checkActiveToolConstraints("any_tool"));
        assertTrue(resolver.checkActiveToolConstraints("another_tool"));
    }

    @Test
    void setAllowedTools_onlyThoseToolsPass() {
        resolver.setAllowedTools(Set.of("read_file", "grep"));

        assertTrue(resolver.checkActiveToolConstraints("read_file"));
        assertTrue(resolver.checkActiveToolConstraints("grep"));
        assertFalse(resolver.checkActiveToolConstraints("write_file"));
        assertFalse(resolver.checkActiveToolConstraints("bash"));
    }

    @Test
    void setAllowedTools_skillToolsAlwaysAllowed() {
        resolver.setAllowedTools(Set.of("read_file"));

        // skill_load and skill_list are always allowed per the implementation
        assertTrue(resolver.checkActiveToolConstraints("skill_load"));
        assertTrue(resolver.checkActiveToolConstraints("skill_list"));
    }

    @Test
    void clearAllowedTools_restoresUnrestricted() {
        resolver.setAllowedTools(Set.of("read_file"));
        assertFalse(resolver.checkActiveToolConstraints("bash"));

        resolver.clearAllowedTools();
        assertNull(resolver.getActiveToolConstraints());
        assertTrue(resolver.checkActiveToolConstraints("bash"));
    }

    @Test
    void setAllowedTools_lifecycle() {
        // 1. Initially unrestricted
        assertTrue(resolver.checkActiveToolConstraints("any_tool"));

        // 2. Set constraints
        resolver.setAllowedTools(Set.of("tool_a", "tool_b"));
        assertTrue(resolver.checkActiveToolConstraints("tool_a"));
        assertFalse(resolver.checkActiveToolConstraints("tool_c"));

        // 3. Update constraints
        resolver.setAllowedTools(Set.of("tool_c"));
        assertFalse(resolver.checkActiveToolConstraints("tool_a"));
        assertTrue(resolver.checkActiveToolConstraints("tool_c"));

        // 4. Clear
        resolver.clearAllowedTools();
        assertTrue(resolver.checkActiveToolConstraints("tool_a"));
        assertTrue(resolver.checkActiveToolConstraints("tool_c"));
    }

    // ===== Plan mode =====

    @Test
    void planMode_defaultOff() {
        assertFalse(resolver.isPlanMode());
    }

    @Test
    void planMode_blocksWriteTools() {
        registerTool("write_file", ToolSideEffect.WRITE);
        resolver.setPlanMode(true);

        assertThrows(
                PlanModeViolationException.class,
                () -> resolver.checkPlanModeRestriction("write_file"));
    }

    @Test
    void planMode_blocksSystemChangeTools() {
        registerTool("bash", ToolSideEffect.SYSTEM_CHANGE);
        resolver.setPlanMode(true);

        assertThrows(
                PlanModeViolationException.class, () -> resolver.checkPlanModeRestriction("bash"));
    }

    @Test
    void planMode_allowsReadOnlyTools() {
        registerTool("read_file", ToolSideEffect.READ_ONLY);
        resolver.setPlanMode(true);

        // Should NOT throw
        assertDoesNotThrow(() -> resolver.checkPlanModeRestriction("read_file"));
    }

    @Test
    void planMode_disabledDoesNotBlock() {
        registerTool("write_file", ToolSideEffect.WRITE);
        resolver.setPlanMode(false);

        assertDoesNotThrow(() -> resolver.checkPlanModeRestriction("write_file"));
    }

    // ===== Permission resolution =====

    @Test
    void resolvePermission_readOnlyDefaultsToAllowed() {
        assertEquals(
                ToolPermission.ALLOWED,
                resolver.resolvePermission("read_file", ToolSideEffect.READ_ONLY));
    }

    @Test
    void resolvePermission_writeDefaultsToAllowed() {
        assertEquals(
                ToolPermission.ALLOWED,
                resolver.resolvePermission("write_file", ToolSideEffect.WRITE));
    }

    @Test
    void resolvePermission_systemChangeDefaultsToAsk() {
        assertEquals(
                ToolPermission.ASK,
                resolver.resolvePermission("bash", ToolSideEffect.SYSTEM_CHANGE));
    }

    @Test
    void resolvePermission_toolSpecificOverrideTakesPrecedence() {
        resolver.setToolPermission("bash", ToolPermission.ALLOWED);

        assertEquals(
                ToolPermission.ALLOWED,
                resolver.resolvePermission("bash", ToolSideEffect.SYSTEM_CHANGE));
    }

    @Test
    void resolvePermission_categoryOverrideTakesPrecedenceOverDefault() {
        resolver.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.DENIED);

        assertEquals(
                ToolPermission.DENIED,
                resolver.resolvePermission("bash", ToolSideEffect.SYSTEM_CHANGE));
    }

    @Test
    void resolvePermission_toolSpecificOverrideBeatsCategory() {
        resolver.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.DENIED);
        resolver.setToolPermission("bash", ToolPermission.ALLOWED);

        // Tool-specific wins over category
        assertEquals(
                ToolPermission.ALLOWED,
                resolver.resolvePermission("bash", ToolSideEffect.SYSTEM_CHANGE));
    }

    // ===== Side-effect resolution =====

    @Test
    void resolveSideEffect_registeredTool() {
        registerTool("my_tool", ToolSideEffect.WRITE);

        assertEquals(ToolSideEffect.WRITE, resolver.resolveSideEffect("my_tool"));
    }

    @Test
    void resolveSideEffect_unknownToolDefaultsToSystemChange() {
        assertEquals(ToolSideEffect.SYSTEM_CHANGE, resolver.resolveSideEffect("nonexistent"));
    }

    // ===== Accessor =====

    @Test
    void getPermissionGuard_returnsConfiguredGuard() {
        assertSame(guard, resolver.getPermissionGuard());
    }
}
