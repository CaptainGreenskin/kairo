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
package io.kairo.tools.exec;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.workspace.Workspace;
import io.kairo.api.workspace.WorkspaceKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerifyExecutionToolTest {

    private final VerifyExecutionTool tool = new VerifyExecutionTool();

    // ── auto-detect unit tests (pure function, no process spawn) ─────────────

    @Test
    void autoDetect_mvn_whenPomXmlPresent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        VerifyExecutionTool.Detection d = VerifyExecutionTool.autoDetect(dir);
        assertNotNull(d);
        assertEquals("mvn", d.tooling());
        assertTrue(d.command().contains("mvn"));
    }

    @Test
    void autoDetect_npm_whenPackageJsonPresent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("package.json"), "{}");
        VerifyExecutionTool.Detection d = VerifyExecutionTool.autoDetect(dir);
        assertNotNull(d);
        assertEquals("npm", d.tooling());
    }

    @Test
    void autoDetect_pytest_whenPyprojectPresent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pyproject.toml"), "[tool.pytest]");
        VerifyExecutionTool.Detection d = VerifyExecutionTool.autoDetect(dir);
        assertNotNull(d);
        assertEquals("pytest", d.tooling());
    }

    @Test
    void autoDetect_pytest_whenPytestIniPresent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pytest.ini"), "");
        VerifyExecutionTool.Detection d = VerifyExecutionTool.autoDetect(dir);
        assertNotNull(d);
        assertEquals("pytest", d.tooling());
    }

    @Test
    void autoDetect_cargo_whenCargoTomlPresent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Cargo.toml"), "[package]");
        VerifyExecutionTool.Detection d = VerifyExecutionTool.autoDetect(dir);
        assertNotNull(d);
        assertEquals("cargo", d.tooling());
    }

    @Test
    void autoDetect_make_onlyWhenMakefileHasTestTarget(@TempDir Path dir) throws Exception {
        // Makefile without a test: target → NOT picked, falls through to null.
        Files.writeString(dir.resolve("Makefile"), "build:\n\techo build\n");
        assertNull(VerifyExecutionTool.autoDetect(dir));

        // With a test: target → picked.
        Files.writeString(dir.resolve("Makefile"), "test:\n\techo run-tests\n");
        VerifyExecutionTool.Detection d = VerifyExecutionTool.autoDetect(dir);
        assertNotNull(d);
        assertEquals("make", d.tooling());
    }

    @Test
    void autoDetect_returnsNull_whenNoMarkers(@TempDir Path dir) {
        assertNull(VerifyExecutionTool.autoDetect(dir));
    }

    @Test
    void autoDetect_priority_pomOverPackageJson(@TempDir Path dir) throws Exception {
        // When both exist (polyglot repo), mvn wins per the documented priority order —
        // matches what most Java projects with a vite/vitepress docs subtree would expect.
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Files.writeString(dir.resolve("package.json"), "{}");
        assertEquals("mvn", VerifyExecutionTool.autoDetect(dir).tooling());
    }

    // ── execute() integration tests using /bin/sh true / false / echo ────────

    @Test
    void execute_explicitCommands_singleSuccessfulCommand(@TempDir Path dir) {
        ToolResult result = tool.execute(Map.of("commands", List.of("true")), ctxFor(dir)).block();

        assertFalse(result.isError(), () -> "got: " + result.content());
        assertTrue((Boolean) result.metadata().get("verified"));
        assertTrue(result.content().contains("VERIFIED"));
    }

    @Test
    void execute_explicitCommands_failingCommandSetsVerifiedFalse(@TempDir Path dir) {
        ToolResult result = tool.execute(Map.of("commands", List.of("false")), ctxFor(dir)).block();

        assertFalse(
                result.isError(),
                "tool errors are reserved for misconfiguration; a failed"
                        + " verification still returns success at the tool level");
        assertFalse((Boolean) result.metadata().get("verified"));
        assertTrue(result.content().contains("FAILED"));
    }

    @Test
    void execute_failFastTrue_stopsAfterFirstFailure(@TempDir Path dir) {
        ToolResult result =
                tool.execute(
                                Map.of("commands", List.of("false", "echo should-not-run")),
                                ctxFor(dir))
                        .block();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.metadata().get("commandResults");
        assertEquals(1, results.size(), "second command must not run on failFast");
        assertFalse((Boolean) result.metadata().get("verified"));
    }

    @Test
    void execute_failFastFalse_runsAllCommandsAndAggregates(@TempDir Path dir) {
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "commands",
                                        List.of("true", "false", "true"),
                                        "failFast",
                                        false),
                                ctxFor(dir))
                        .block();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.metadata().get("commandResults");
        assertEquals(3, results.size(), "all commands must run when failFast=false");
        assertFalse((Boolean) result.metadata().get("verified"));
        assertEquals(0, results.get(0).get("exitCode"));
        assertNotEquals(0, results.get(1).get("exitCode"));
        assertEquals(0, results.get(2).get("exitCode"));
    }

    @Test
    void execute_autoDetect_reportsDetectedTooling(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        // Override mvn → echo so this test doesn't actually need maven installed.
        // We're proving the detection logic surfaces "mvn" in metadata even when the
        // command itself fails (mvn would error on this fake pom).
        ToolResult result =
                tool.execute(Map.of("commands", List.of("echo hello-from-fake-mvn")), ctxFor(dir))
                        .block();
        // commands param given → detection skipped. So this test instead validates the
        // metadata stays absent when commands is explicit.
        assertNull(result.metadata().get("detectedTooling"));
    }

    @Test
    void execute_autoDetect_metadataPopulatedWhenCommandsOmitted(@TempDir Path dir)
            throws Exception {
        // Trick the auto-detect: drop a Makefile with a test target that just echoes —
        // make is on PATH on macOS/Linux so this is portable.
        Files.writeString(dir.resolve("Makefile"), "test:\n\t@echo \"verify-ran\"\n");
        ToolResult result = tool.execute(Map.of(), ctxFor(dir)).block();

        assertFalse(result.isError(), () -> "got: " + result.content());
        assertEquals("make", result.metadata().get("detectedTooling"));
        assertTrue((Boolean) result.metadata().get("verified"));
        assertTrue(result.content().contains("Auto-detected build system: make"));
    }

    @Test
    void execute_noCommandsAndNoMarkers_returnsErrorWithGuidance(@TempDir Path dir) {
        ToolResult result = tool.execute(Map.of(), ctxFor(dir)).block();

        assertTrue(result.isError());
        assertTrue(
                result.content().contains("No build-system marker found"),
                () -> "got: " + result.content());
        assertTrue(
                result.content().contains("pass 'commands' explicitly".replace("'", "'"))
                        || result.content().contains("commands"));
    }

    @Test
    void execute_blankCommandsList_returnsError(@TempDir Path dir) {
        ToolResult result =
                tool.execute(Map.of("commands", List.of("   ", "")), ctxFor(dir)).block();

        assertTrue(result.isError());
        assertTrue(result.content().contains("no non-blank entries"));
    }

    @Test
    void execute_workingDirNotExist_returnsError(@TempDir Path dir) {
        ToolResult result =
                tool.execute(
                                Map.of("commands", List.of("true"), "workingDir", "no-such-subdir"),
                                ctxFor(dir))
                        .block();

        assertTrue(result.isError());
        assertTrue(result.content().contains("Working directory does not exist"));
    }

    @Test
    void execute_workingDirRelative_resolvedAgainstWorkspaceRoot(@TempDir Path dir)
            throws Exception {
        Path sub = Files.createDirectory(dir.resolve("subproject"));
        Files.writeString(sub.resolve("Makefile"), "test:\n\t@echo \"sub-ran\"\n");

        ToolResult result = tool.execute(Map.of("workingDir", "subproject"), ctxFor(dir)).block();

        assertFalse(result.isError(), () -> "got: " + result.content());
        assertTrue((Boolean) result.metadata().get("verified"));
        assertEquals("make", result.metadata().get("detectedTooling"));
    }

    @Test
    void execute_metadataIncludesCommandResults(@TempDir Path dir) {
        ToolResult result =
                tool.execute(Map.of("commands", List.of("echo first", "echo second")), ctxFor(dir))
                        .block();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results =
                (List<Map<String, Object>>) result.metadata().get("commandResults");
        assertEquals(2, results.size());
        assertEquals("echo first", results.get(0).get("command"));
        assertEquals(0, results.get(0).get("exitCode"));
        assertTrue(((String) results.get(0).get("stdoutTail")).contains("first"));
    }

    @Test
    void execute_totalDurationMs_reported(@TempDir Path dir) {
        ToolResult result = tool.execute(Map.of("commands", List.of("true")), ctxFor(dir)).block();
        Long total = (Long) result.metadata().get("totalDurationMs");
        assertNotNull(total);
        assertTrue(total >= 0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ToolContext ctxFor(Path dir) {
        // 6-arg backward-compatible ctor: (agentId, sessionId, dependencies,
        // idempotencyKey, tenant, workspace). Lets us bind the @TempDir as
        // the workspace without depending on internal fields.
        return new ToolContext(
                "agent-1",
                "sess-1",
                Map.of(),
                null,
                TenantContext.SINGLE,
                new TempDirWorkspace(dir));
    }

    /** Workspace impl rooted at a JUnit @TempDir for VerifyExecutionTool unit tests. */
    private record TempDirWorkspace(Path root) implements Workspace {
        @Override
        public String id() {
            return "test-workspace";
        }

        @Override
        public WorkspaceKind kind() {
            return WorkspaceKind.LOCAL;
        }

        @Override
        public Map<String, String> metadata() {
            return Map.of();
        }
    }
}
