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
package io.kairo.expertteam.role;

import io.kairo.api.team.RoleDefinition;
import java.util.List;
import java.util.Objects;

/**
 * Extended profile for an expert role, enriching the base {@link RoleDefinition} with skill
 * mounting, memory namespace, model escalation, and structured capabilities for dynamic role
 * matching.
 */
public record ExpertProfile(
        String roleId,
        RoleDefinition roleDefinition,
        String skillProfile,
        List<String> mountedSkills,
        String memoryNamespace,
        String modelOverride, // nullable — senior model ID for escalation
        RoleCapabilities capabilities) {

    /** Backward-compatible constructor without capabilities (defaults to EMPTY). */
    public ExpertProfile(
            String roleId,
            RoleDefinition roleDefinition,
            String skillProfile,
            List<String> mountedSkills,
            String memoryNamespace,
            String modelOverride) {
        this(
                roleId,
                roleDefinition,
                skillProfile,
                mountedSkills,
                memoryNamespace,
                modelOverride,
                RoleCapabilities.EMPTY);
    }

    public ExpertProfile {
        Objects.requireNonNull(roleId, "roleId must not be null");
        Objects.requireNonNull(roleDefinition, "roleDefinition must not be null");
        Objects.requireNonNull(skillProfile, "skillProfile must not be null");
        mountedSkills = mountedSkills != null ? List.copyOf(mountedSkills) : List.of();
        if (memoryNamespace == null || memoryNamespace.isBlank()) {
            memoryNamespace = roleId; // default namespace = roleId
        }
        if (capabilities == null) {
            capabilities = RoleCapabilities.EMPTY;
        }
    }
}
