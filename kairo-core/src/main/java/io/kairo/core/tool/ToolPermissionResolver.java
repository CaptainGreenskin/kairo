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
import io.kairo.core.tool.permission.PermissionMode;
import io.kairo.core.tool.permission.PermissionRule;
import io.kairo.core.tool.permission.PermissionRuleEngine;
import io.kairo.core.tool.permission.PermissionSettings;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private volatile PermissionMode mode = PermissionMode.DEFAULT;
    private volatile PermissionMode previousMode = null;
    private volatile PermissionRuleEngine ruleEngine = new PermissionRuleEngine(List.of());
    private final Map<String, ToolPermission> toolPermissions = new ConcurrentHashMap<>();
    private volatile Set<String> activeToolConstraints = null; // null = no restriction
    private volatile Path workspaceRoot = null; // null = no boundary enforcement

    /** Arg keys that may carry a file path, mirroring {@code PathTraversalPolicy}. */
    private static final List<String> PATH_ARG_KEYS =
            List.of("file_path", "filePath", "path", "directory", "dir", "target");

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
     * Set the permission mode.
     *
     * @param mode the new permission mode
     */
    public void setMode(PermissionMode mode) {
        this.mode = mode;
    }

    /**
     * @return the current permission mode
     */
    public PermissionMode getMode() {
        return mode;
    }

    /**
     * Set the permission rules for the rule engine.
     *
     * @param rules the ordered list of rules
     */
    public void setRules(List<PermissionRule> rules) {
        this.ruleEngine = new PermissionRuleEngine(rules);
    }

    /**
     * Apply a complete permission settings configuration.
     *
     * @param settings the settings to apply
     */
    public void applySettings(PermissionSettings settings) {
        if (settings.mode() != null) {
            this.mode = settings.mode();
        }
        this.ruleEngine = new PermissionRuleEngine(settings.rules());
    }

    /**
     * Set plan mode on or off (backward-compatible convenience method).
     *
     * @param planMode true to enter plan mode, false to exit
     */
    public void setPlanMode(boolean planMode) {
        if (planMode) {
            this.previousMode = this.mode;
            this.mode = PermissionMode.PLAN;
        } else {
            this.mode = previousMode != null ? previousMode : PermissionMode.DEFAULT;
            this.previousMode = null;
        }
    }

    /**
     * Check if plan mode is currently active.
     *
     * @return true if in plan mode
     */
    public boolean isPlanMode() {
        return mode == PermissionMode.PLAN;
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
     * Set the workspace root that confines file WRITE operations. When set, a WRITE-side-effect
     * tool whose resolved target path escapes this root is escalated to {@link ToolPermission#ASK}
     * (human approval), so writes outside the workspace require explicit user consent rather than
     * proceeding silently. Pass {@code null} to disable boundary enforcement.
     *
     * @param root the workspace root directory, or null to disable
     */
    public void setWorkspaceRoot(Path root) {
        this.workspaceRoot = root != null ? root.toAbsolutePath().normalize() : null;
    }

    /**
     * Check plan mode restrictions before executing a tool.
     *
     * @param toolName the tool name
     * @throws PlanModeViolationException if the tool is blocked in plan mode
     */
    public void checkPlanModeRestriction(String toolName) {
        if (mode == PermissionMode.PLAN) {
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
     * Resolve the permission level for a tool (backward-compatible, no arg matching).
     *
     * @param toolName the tool name
     * @param sideEffect the side-effect classification
     * @return the resolved permission
     */
    public ToolPermission resolvePermission(String toolName, ToolSideEffect sideEffect) {
        return resolvePermission(toolName, sideEffect, Map.of());
    }

    /**
     * Resolve the permission level for a tool with argument-level matching.
     *
     * <p>Resolution order:
     *
     * <ol>
     *   <li>Programmatic tool-specific override ({@link #setToolPermission})
     *   <li>File-based rule engine ({@link PermissionRuleEngine})
     *   <li>Programmatic category override ({@link #setDefaultPermission})
     *   <li>Mode-based default ({@link PermissionMode#defaultPermission})
     * </ol>
     *
     * @param toolName the tool name
     * @param sideEffect the side-effect classification
     * @param args the tool arguments for rule matching
     * @return the resolved permission
     */
    public ToolPermission resolvePermission(
            String toolName, ToolSideEffect sideEffect, Map<String, Object> args) {
        ToolPermission base = resolveBasePermission(toolName, sideEffect, args);

        // Workspace boundary: a WRITE whose target escapes the workspace root requires human
        // approval. Never loosen an explicit DENIED, and respect BYPASS ("trust everything").
        if (base != ToolPermission.DENIED
                && mode != PermissionMode.BYPASS
                && sideEffect == ToolSideEffect.WRITE
                && escapesWorkspace(args)) {
            return ToolPermission.ASK;
        }
        return base;
    }

    private ToolPermission resolveBasePermission(
            String toolName, ToolSideEffect sideEffect, Map<String, Object> args) {
        // 1. Programmatic tool-specific override
        var toolPerm = toolPermissions.get(toolName);
        if (toolPerm != null) return toolPerm;

        // 2. File-based rule engine
        Optional<ToolPermission> rulePerm = ruleEngine.resolve(toolName, args);
        if (rulePerm.isPresent()) return rulePerm.get();

        // 3. Programmatic category override
        var categoryPerm = toolPermissions.get("__category__" + sideEffect.name());
        if (categoryPerm != null) return categoryPerm;

        // 4. Mode-based default
        return mode.defaultPermission(sideEffect);
    }

    /**
     * True when {@link #workspaceRoot} is set and any path argument resolves outside it. Relative
     * paths are resolved against the root (matching how file tools resolve), so both absolute paths
     * and {@code ../} traversal that escape the workspace are caught. Handles single-path tools
     * ({@code write}/{@code edit}) via {@link #PATH_ARG_KEYS} and batch tools ({@code batch_write})
     * whose {@code files} array carries one {@code path} per entry.
     */
    private boolean escapesWorkspace(Map<String, Object> args) {
        Path root = this.workspaceRoot;
        if (root == null || args == null) return false;
        for (String key : PATH_ARG_KEYS) {
            if (args.get(key) instanceof String s && escapesRoot(root, s)) return true;
        }
        // batch_write-style: { files: [ { path|file_path: "..." }, ... ] }
        if (args.get("files") instanceof List<?> files) {
            for (Object item : files) {
                if (item instanceof Map<?, ?> entry) {
                    Object p = entry.get("path");
                    if (!(p instanceof String)) p = entry.get("file_path");
                    if (p instanceof String s && escapesRoot(root, s)) return true;
                }
            }
        }
        return false;
    }

    /** True when {@code filePath}, resolved against {@code root}, falls outside it. */
    private static boolean escapesRoot(Path root, String filePath) {
        if (filePath == null || filePath.isBlank()) return false;
        try {
            Path target = root.resolve(filePath).toAbsolutePath().normalize();
            return !target.startsWith(root);
        } catch (InvalidPathException e) {
            // Unparseable path → let downstream guardrails/tools reject it; don't force approval.
            return false;
        }
    }
}
