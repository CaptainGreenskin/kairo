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

/**
 * Built-in expert roles for team-based task decomposition. Maps to {@code RoleDefinition.roleId}
 * via the "expert:" prefix convention.
 */
public enum ExpertRole {
    ARCHITECT("expert:architect", "System design and decision arbitration"),
    RESEARCHER("expert:researcher", "Information gathering and analysis"),
    CODER("expert:coder", "Code implementation and modification"),
    REVIEWER("expert:reviewer", "Code review and quality assessment"),
    TESTER("expert:tester", "Test writing and execution"),
    SYNTHESIZER("expert:synthesizer", "Result integration and report generation");

    private final String roleId;
    private final String description;

    ExpertRole(String roleId, String description) {
        this.roleId = roleId;
        this.description = description;
    }

    public String roleId() {
        return roleId;
    }

    public String description() {
        return description;
    }

    /**
     * Resolve from roleId string (e.g., "expert:coder").
     *
     * @return the matching ExpertRole or null if not a built-in role
     */
    public static ExpertRole fromRoleId(String roleId) {
        if (roleId == null) return null;
        for (ExpertRole role : values()) {
            if (role.roleId.equals(roleId)) return role;
        }
        return null;
    }
}
