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
package io.kairo.multiagent.subagent;

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
                defaultCapabilities(role),
                defaultContextScopes(role));
    }

    private static RoleCapabilities defaultCapabilities(ExpertRole role) {
        return switch (role) {
            case RESEARCHER ->
                    new RoleCapabilities(
                            Set.of(),
                            Set.of(),
                            Set.of("documentation", "observability", "dependencies"),
                            Set.of("research", "analyze"));
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
                            Set.of("security", "refactoring", "quality"),
                            Set.of("review"));
            case TESTER ->
                    new RoleCapabilities(
                            Set.of(),
                            Set.of(),
                            Set.of("testing", "verification"),
                            Set.of("test", "verify"));
            case DEBUGGER ->
                    new RoleCapabilities(
                            Set.of(),
                            Set.of(),
                            Set.of("debugging", "diagnostics", "observability"),
                            Set.of("diagnose", "debug", "analyze"));
        };
    }

    private static Set<ContextScope> defaultContextScopes(ExpertRole role) {
        return switch (role) {
            case RESEARCHER -> Set.of(ContextScope.FULL_TREE, ContextScope.KEY_FILES);
            case CODER -> Set.of(ContextScope.SOURCE_FILES, ContextScope.UPSTREAM_ONLY);
            case TESTER -> Set.of(ContextScope.TEST_FILES, ContextScope.UPSTREAM_ONLY);
            case REVIEWER -> Set.of(ContextScope.UPSTREAM_ONLY);
            case DEBUGGER -> Set.of(ContextScope.SOURCE_FILES, ContextScope.UPSTREAM_ONLY);
        };
    }

    private static String defaultInstructions(ExpertRole role) {
        return switch (role) {
            case RESEARCHER ->
                    "You are a researcher. Investigate the codebase, locate relevant code, trace"
                            + " dependencies, and provide comprehensive findings. In your findings,"
                            + " always reference files by their full workspace-relative path. When you"
                            + " identify a directory as relevant, enumerate the specific files within it"
                            + " rather than just naming the directory.";
            case CODER ->
                    "You are a full-stack engineer. Implement and modify code across frontend,"
                            + " backend, and database layers following project conventions. Write clean,"
                            + " production-quality code. Relevant source files are available from upstream"
                            + " outputs \u2014 only use read_file for files NOT already provided.";
            case REVIEWER ->
                    "You are a code reviewer. Evaluate code quality, correctness, security, and"
                            + " adherence to best practices. Identify risks and provide actionable"
                            + " improvement suggestions. Code to review is provided in upstream outputs.";
            case TESTER ->
                    "You are a QA engineer. Run tests and build processes, collect verification"
                            + " results, and report evidence of correctness or failure. Write tests"
                            + " covering happy paths, edge cases, and error conditions when needed.";
            case DEBUGGER ->
                    "You are a debugger. Reproduce faults, trace root causes through logs and code,"
                            + " and diagnose defects. Provide a clear root-cause analysis with specific"
                            + " file locations and a recommended fix. Do not fix the code yourself \u2014"
                            + " describe the fix for the Coder.";
        };
    }

    private static List<String> defaultAllowedTools(ExpertRole role) {
        return switch (role) {
            // Researcher keeps all tools \u2014 sole discoverer with full workspace access.
            case RESEARCHER -> List.of();
            case CODER ->
                    List.of(
                            "read_file",
                            "write_file",
                            "edit_file",
                            "grep",
                            "search",
                            "bash",
                            "run_tests");
            // Reviewer: read-only inspection of code and diffs.
            case REVIEWER -> List.of("read_file", "grep", "search", "list_files");
            // Tester: read + write tests + run them.
            case TESTER ->
                    List.of("read_file", "write_file", "grep", "search", "list_files", "run_tests");
            // Debugger: read + run diagnostics (bash for logs/stack traces), no file writes.
            case DEBUGGER -> List.of("read_file", "grep", "search", "bash", "list_files");
        };
    }
}
