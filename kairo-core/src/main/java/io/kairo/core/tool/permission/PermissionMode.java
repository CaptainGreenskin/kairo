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
package io.kairo.core.tool.permission;

import io.kairo.api.tool.ToolPermission;
import io.kairo.api.tool.ToolSideEffect;

/**
 * Permission modes that control the default approval behavior for tool invocations.
 *
 * <p>Each mode defines a mapping from {@link ToolSideEffect} to {@link ToolPermission} that serves
 * as the fallback when no explicit rule or programmatic override matches.
 */
public enum PermissionMode {

    /** Current Kairo default: WRITE auto-approved, SYSTEM_CHANGE requires approval. */
    DEFAULT,

    /** Read-only mode: only READ_ONLY tools allowed, all others denied. */
    PLAN,

    /** All non-read tools require approval. */
    STRICT,

    /** Everything auto-approved — no approval prompts. */
    BYPASS;

    /**
     * Returns the default permission for a given side-effect level under this mode.
     *
     * @param sideEffect the tool's side-effect classification
     * @return the default permission
     */
    public ToolPermission defaultPermission(ToolSideEffect sideEffect) {
        return switch (this) {
            case DEFAULT ->
                    sideEffect == ToolSideEffect.SYSTEM_CHANGE
                            ? ToolPermission.ASK
                            : ToolPermission.ALLOWED;
            case PLAN ->
                    sideEffect == ToolSideEffect.READ_ONLY
                            ? ToolPermission.ALLOWED
                            : ToolPermission.DENIED;
            case STRICT ->
                    sideEffect == ToolSideEffect.READ_ONLY
                            ? ToolPermission.ALLOWED
                            : ToolPermission.ASK;
            case BYPASS -> ToolPermission.ALLOWED;
        };
    }
}
