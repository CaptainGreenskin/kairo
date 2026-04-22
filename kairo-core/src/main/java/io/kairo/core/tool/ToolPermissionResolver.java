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

import io.kairo.api.exception.PlanModeViolationException;
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolPermission;
import io.kairo.api.tool.ToolSideEffect;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves tool permissions, plan-mode restrictions, and active-tool constraints.
 *
 * <p>Extracted from {@link DefaultToolExecutor} pipeline.
 */
public final class ToolPermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(ToolPermissionResolver.class);

    private final PermissionGuard permissionGuard;
    private final DefaultToolRegistry registry;
    private volatile boolean planMode = false;
    private final Map<String, ToolPermission> toolPermissions = new ConcurrentHashMap<>();
    private volatile Set<String> activeToolConstraints = null; // null = no restriction

    /**
     * Create a new permission resolver.
     *
     * @param permissionGuard the permission guard for command-level checks
     * @param registry the tool registry for side-effect lookup
     */
    public ToolPermissionResolver(PermissionGuard permissionGuard, DefaultToolRegistry registry) {
        this.permissionGuard = permissionGuard;
        this.registry = registry;
    }

    /**
     * @return the underlying permission guard
     */
    public PermissionGuard getPermissionGuard() {
        return permissionGuard;
    }

    /**
     * Set plan mode on or off.
     *
     * @param planMode true to enter plan mode, false to exit
     */
    public void setPlanMode(boolean planMode) {
        this.planMode = planMode;
    }

    /**
     * Check if plan mode is currently active.
     *
     * @return true if in plan mode
     */
    public boolean isPlanMode() {
        return planMode;
    }

    /**
     * Set a tool-specific permission override.
     *
     * @param toolName the tool name
     * @param permission the permission level
     */
    public void setToolPermission(String toolName, ToolPermission permission) {
        toolPermissions.put(toolName, permission);
    }

    /**
     * Set a default permission for all tools with the given side-effect classification.
     *
     * @param sideEffect the side-effect category
     * @param permission the permission level
     */
    public void setDefaultPermission(ToolSideEffect sideEffect, ToolPermission permission) {
        toolPermissions.put("__category__" + sideEffect.name(), permission);
    }

    /**
     * Set the allowed tools whitelist for the currently active skill.
     *
     * @param allowed set of tool names that are allowed, or null to clear
     */
    public void setAllowedTools(Set<String> allowed) {
        this.activeToolConstraints = allowed;
    }

    /** Clear the active tool constraints, allowing all tools to execute. */
    public void clearAllowedTools() {
        this.activeToolConstraints = null;
    }

    /**
     * Check plan mode restrictions before executing a tool.
     *
     * @param toolName the tool name
     * @throws PlanModeViolationException if the tool is blocked in plan mode
     */
    public void checkPlanModeRestriction(String toolName) {
        if (planMode) {
            var sideEffect = resolveSideEffect(toolName);
            if (sideEffect == ToolSideEffect.WRITE || sideEffect == ToolSideEffect.SYSTEM_CHANGE) {
                throw new PlanModeViolationException(
                        "Tool '"
                                + toolName
                                + "' ("
                                + sideEffect
                                + ") is blocked in Plan Mode. "
                                + "Only read-only tools are available. Exit plan mode first.",
                        toolName);
            }
        }
    }

    /**
     * Check active tool constraints.
     *
     * @param toolName the tool name
     * @return true if the tool is allowed by constraints, false if blocked
     */
    public boolean checkActiveToolConstraints(String toolName) {
        Set<String> constraints = this.activeToolConstraints; // defensive local copy
        if (constraints == null) return true;
        if (constraints.contains(toolName)) return true;
        return "skill_load".equals(toolName) || "skill_list".equals(toolName);
    }

    /**
     * @return the current active tool constraints, or null if unrestricted
     */
    public Set<String> getActiveToolConstraints() {
        return activeToolConstraints;
    }

    /**
     * Resolve the side-effect classification for a tool by name.
     *
     * <p>Unknown tools default to {@link ToolSideEffect#SYSTEM_CHANGE} (safest assumption).
     *
     * @param toolName the tool name
     * @return the side-effect classification
     */
    public ToolSideEffect resolveSideEffect(String toolName) {
        var def = registry.get(toolName);
        if (def.isEmpty()) {
            log.warn(
                    "Tool '{}' has no registered definition, defaulting to SYSTEM_CHANGE",
                    toolName);
            return ToolSideEffect.SYSTEM_CHANGE;
        }
        return def.get().sideEffect();
    }

    /**
     * Resolve the permission level for a tool.
     *
     * <p>Resolution order: tool-specific override → category-level (by SideEffect) → default.
     * Defaults: READ_ONLY and WRITE → ALLOWED, SYSTEM_CHANGE → ASK.
     *
     * @param toolName the tool name
     * @param sideEffect the side-effect classification
     * @return the resolved permission
     */
    public ToolPermission resolvePermission(String toolName, ToolSideEffect sideEffect) {
        // 1. Tool-specific override
        var toolPerm = toolPermissions.get(toolName);
        if (toolPerm != null) return toolPerm;

        // 2. Category-level (by SideEffect)
        var categoryPerm = toolPermissions.get("__category__" + sideEffect.name());
        if (categoryPerm != null) return categoryPerm;

        // 3. Default: READ_ONLY → ALLOWED, WRITE → ALLOWED, SYSTEM_CHANGE → ASK
        return sideEffect == ToolSideEffect.SYSTEM_CHANGE
                ? ToolPermission.ASK
                : ToolPermission.ALLOWED;
    }
}
