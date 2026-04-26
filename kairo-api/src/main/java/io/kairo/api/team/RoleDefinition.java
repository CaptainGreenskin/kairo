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
package io.kairo.api.team;

import io.kairo.api.Experimental;
import java.util.List;
import java.util.Objects;

/**
 * Immutable definition of a role that can be bound to a {@link TeamStep}.
 *
 * <p>A role couples human-readable intent (name, instructions) with a capability requirement
 * (ADR-017) and an optional tool allowlist. Roles are resolved at plan time; an unresolvable role
 * causes the coordinator to fail fast in planning rather than mid-execution (ADR-015 fix #4).
 *
 * @param roleId stable identifier for the role; non-null, non-blank
 * @param roleName human-readable role name; non-null, non-blank
 * @param instructions system-style instructions for the bound agent; non-null
 * @param requiredCapability capability name (see ADR-017) required to satisfy this role; non-null,
 *     non-blank
 * @param allowedTools optional tool allowlist; defensively copied, never {@code null}
 * @since v0.10 (Experimental)
 */
@Experimental("Team role definition; introduced in v0.10, targeting stabilization in v1.1")
public record RoleDefinition(
        String roleId,
        String roleName,
        String instructions,
        String requiredCapability,
        List<String> allowedTools) {

    public RoleDefinition {
        requireNonBlank(roleId, "roleId");
        requireNonBlank(roleName, "roleName");
        Objects.requireNonNull(instructions, "instructions must not be null");
        requireNonBlank(requiredCapability, "requiredCapability");
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be null or blank");
        }
    }
}
