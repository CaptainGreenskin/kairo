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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of expert profiles. Pre-loaded with built-in roles; extensible via {@link
 * #register(String, ExpertProfile)} for custom roles.
 */
public class ExpertRoleRegistry {

    private final ConcurrentHashMap<String, ExpertProfile> profiles = new ConcurrentHashMap<>();

    public ExpertRoleRegistry() {
        for (ExpertRole role : ExpertRole.values()) {
            register(role.roleId(), defaultProfile(role));
        }
    }

    public void register(String roleId, ExpertProfile profile) {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(profile, "profile");
        profiles.put(roleId, profile);
    }

    public Optional<ExpertProfile> resolve(String roleId) {
        return Optional.ofNullable(profiles.get(roleId));
    }

    public List<ExpertProfile> allProfiles() {
        return List.copyOf(profiles.values());
    }

    public Set<String> registeredRoleIds() {
        return Set.copyOf(profiles.keySet());
    }

    private static ExpertProfile defaultProfile(ExpertRole role) {
        RoleDefinition roleDef =
                new RoleDefinition(
                        role.roleId(),
                        role.name().charAt(0) + role.name().substring(1).toLowerCase(),
                        defaultInstructions(role),
                        "agent.default",
                        defaultAllowedTools(role));
        return new ExpertProfile(
                role.roleId(),
                roleDef,
                role.description(),
                List.of(),
                role.roleId(),
                null,
                defaultCapabilities(role));
    }

    private static RoleCapabilities defaultCapabilities(ExpertRole role) {
        return switch (role) {
            case ARCHITECT ->
                    new RoleCapabilities(
                            Set.of(),
                            Set.of(),
                            Set.of("architecture", "api", "performance"),
                            Set.of("design", "research"));
            case RESEARCHER ->
                    new RoleCapabilities(
                            Set.of(),
                            Set.of(),
                            Set.of("documentation", "observability"),
                            Set.of("research"));
            case CODER ->
                    new RoleCapabilities(
                            Set.of(),
                            Set.of(),
                            Set.of(
                                    "api",
                                    "backend",
                                    "frontend",
                                    "database",
                                    "refactoring",
                                    "performance"),
                            Set.of("implement", "debug", "integrate"));
            case REVIEWER ->
                    new RoleCapabilities(
                            Set.of(),
                            Set.of(),
                            Set.of("security", "refactoring"),
                            Set.of("review"));
            case TESTER ->
                    new RoleCapabilities(
                            Set.of(), Set.of(), Set.of("testing"), Set.of("test", "debug"));
            case SYNTHESIZER ->
                    new RoleCapabilities(
                            Set.of(),
                            Set.of(),
                            Set.of("documentation"),
                            Set.of("synthesize", "integrate"));
        };
    }

    private static String defaultInstructions(ExpertRole role) {
        return switch (role) {
            case ARCHITECT ->
                    "You are a system architect. Analyze requirements, design solutions, make technology decisions, and resolve conflicts between team members.";
            case RESEARCHER ->
                    "You are a researcher. Gather information, analyze codebases, read documentation, and provide comprehensive findings to support the team.";
            case CODER ->
                    "You are a software engineer. Write clean, tested, production-quality code following project conventions.";
            case REVIEWER ->
                    "You are a code reviewer. Evaluate code quality, correctness, security, and adherence to best practices. Provide actionable feedback.";
            case TESTER ->
                    "You are a test engineer. Write comprehensive tests covering happy paths, edge cases, and error conditions.";
            case SYNTHESIZER ->
                    "You are a synthesizer. Integrate outputs from all team members into a coherent final deliverable with summary, changes, and documentation.";
        };
    }

    private static List<String> defaultAllowedTools(ExpertRole role) {
        return switch (role) {
            case REVIEWER -> List.of("read_file", "grep", "search", "list_files"); // read-only
            case TESTER ->
                    List.of("read_file", "write_file", "grep", "search", "list_files", "run_tests");
            default -> List.of(); // empty = all tools allowed
        };
    }
}
