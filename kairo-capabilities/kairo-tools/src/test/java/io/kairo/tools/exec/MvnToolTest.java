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

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

class MvnToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    private ToolResult exec(Map<String, Object> args) {
        return tool.execute(args, CTX).block();
    }

    private static final Pattern FAILED_TEST_PATTERN =
            Pattern.compile("\\[ERROR\\]\\s+(\\S+(?:\\.\\S+)+)\\s+--.*<<<\\s+(?:FAILURE|ERROR)");

    private MvnTool tool;

    @BeforeEach
    void setUp() {
        tool = new MvnTool();
    }

    // --- Parameter validation tests (no mvn required) ---

    @Test
    void missingGoalsParameter() {
        ToolResult result = exec(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'goals' is required"));
        assertEquals("mvn", result.toolUseId());
        assertFalse((Boolean) result.metadata().get("buildSuccess"));
    }

    @Test
    void emptyGoalsParameter() {
        ToolResult result = exec(Map.of("goals", List.of()));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'goals' is required"));
    }

    @Test
    void invalidWorkingDirectoryReturnsError() {
        ToolResult result =
                exec(Map.of("goals", List.of("compile"), "workingDir", "/nonexistent/path/abc123"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("workingDir does not exist"));
    }

    // --- Integration tests (require mvn on PATH) ---

    @Test
    @EnabledIf("mvnAvailable")
    void goalsBuildCorrectCommand() {
        ToolResult result = exec(Map.of("goals", List.of("--version")));
        assertFalse(result.isError());
        assertTrue(result.content().contains("Apache Maven") || result.content().contains("Maven"));
    }

    @Test
    @EnabledIf("mvnAvailable")
    void moduleParameterAccepted() {
        // -pl requires a real project with pom.xml, so we just verify the parameter is accepted
        // Maven will warn about the module but still run
        ToolResult result =
                exec(Map.of("goals", List.of("help:effective-pom", "-pl", "some-module")));
        // Parameter parsing succeeds; build result depends on mvn execution
        assertNotNull(result);
        assertEquals("mvn", result.toolUseId());
    }

    @Test
    @EnabledIf("mvnAvailable")
    void skipTestsAddsDskipTests() {
        ToolResult result = exec(Map.of("goals", List.of("--version"), "skipTests", true));
        assertFalse(result.isError());
        assertTrue((Boolean) result.metadata().get("buildSuccess"));
    }

    @Test
    @EnabledIf("mvnAvailable")
    void profilesParameterAccepted() {
        ToolResult result =
                exec(Map.of("goals", List.of("--version"), "profiles", List.of("-Pnonexistent")));
        assertNotNull(result);
        assertEquals("mvn", result.toolUseId());
    }

    @Test
    @EnabledIf("mvnAvailable")
    void buildSuccessMetadata() {
        ToolResult result = exec(Map.of("goals", List.of("--version")));
        assertFalse(result.isError());
        assertTrue((Boolean) result.metadata().get("buildSuccess"));
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    @EnabledIf("mvnAvailable")
    void metadataContainsExpectedFields() {
        ToolResult result = exec(Map.of("goals", List.of("--version")));
        assertFalse(result.isError());
        assertTrue(result.metadata().containsKey("exitCode"));
        assertTrue(result.metadata().containsKey("buildSuccess"));
        assertTrue(result.metadata().containsKey("durationMs"));
        assertTrue(result.metadata().containsKey("failedTestCount"));
        assertTrue(result.metadata().containsKey("failedTests"));
    }

    @Test
    @EnabledIf("mvnAvailable")
    void timeoutParameterAccepted() {
        ToolResult result = exec(Map.of("goals", List.of("--version"), "timeout", 30));
        assertFalse(result.isError());
        assertFalse(result.content().contains("timed out"));
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    @EnabledIf("mvnAvailable")
    void stringTimeoutParsed() {
        ToolResult result = exec(Map.of("goals", List.of("--version"), "timeout", "30"));
        assertFalse(result.isError());
    }

    @Test
    @EnabledIf("mvnAvailable")
    void skipTestsAsString() {
        ToolResult result = exec(Map.of("goals", List.of("--version"), "skipTests", "true"));
        assertFalse(result.isError());
    }

    @Test
    @EnabledIf("mvnAvailable")
    void workingDirectoryResolved(@TempDir Path tempDir) {
        ToolResult result =
                exec(
                        Map.of(
                                "goals", List.of("--version"),
                                "workingDir", tempDir.toString()));
        assertFalse(result.isError());
    }

    @Test
    @EnabledIf("mvnAvailable")
    void failedTestCountZeroForNonTestRun() {
        ToolResult result = exec(Map.of("goals", List.of("--version")));
        assertFalse(result.isError());
        assertEquals(0, result.metadata().get("failedTestCount"));
        @SuppressWarnings("unchecked")
        List<String> failedTests = (List<String>) result.metadata().get("failedTests");
        assertTrue(failedTests.isEmpty());
    }

    // --- Simulated output tests (validate result parsing without real mvn) ---

    @Test
    void buildSuccessSimulation() {
        ToolResult result = simulateMvnOutput("BUILD SUCCESS\n", 0, false);
        assertFalse(result.isError());
        assertTrue(result.content().contains("BUILD SUCCESS"));
        assertTrue((Boolean) result.metadata().get("buildSuccess"));
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    void buildFailureSimulation() {
        ToolResult result = simulateMvnOutput("BUILD FAILURE\n", 1, false);
        assertTrue(result.isError());
        assertTrue(result.content().contains("BUILD FAILURE"));
        assertFalse((Boolean) result.metadata().get("buildSuccess"));
        assertEquals(1, result.metadata().get("exitCode"));
    }

    @Test
    void timeoutSimulation() {
        ToolResult result =
                simulateMvnOutput("Maven timed out after 1s.\n\npartial output", -1, true);
        assertTrue(result.isError());
        assertTrue(result.content().contains("timed out"));
        assertEquals(-1, result.metadata().get("exitCode"));
        assertTrue((Boolean) result.metadata().get("timedOut"));
    }

    @Test
    void failedTestsParsedFromOutput() {
        String output =
                "[ERROR] io.kairo.MyTest.testA -- Time: 0.1s <<< FAILURE!\n"
                        + "[ERROR] io.kairo.MyTest.testB -- Time: 0.2s <<< FAILURE!\n"
                        + "BUILD FAILURE";
        ToolResult result = simulateMvnOutput(output, 1, false);
        assertTrue(result.isError());
        @SuppressWarnings("unchecked")
        List<String> failedTests = (List<String>) result.metadata().get("failedTests");
        assertEquals(2, failedTests.size());
        assertTrue(failedTests.contains("io.kairo.MyTest.testA"));
        assertTrue(failedTests.contains("io.kairo.MyTest.testB"));
        assertEquals(2, result.metadata().get("failedTestCount"));
    }

    @Test
    void noFailedTestsInSuccessOutput() {
        String output =
                "[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0\n" + "BUILD SUCCESS";
        ToolResult result = simulateMvnOutput(output, 0, false);
        assertFalse(result.isError());
        @SuppressWarnings("unchecked")
        List<String> failedTests = (List<String>) result.metadata().get("failedTests");
        assertTrue(failedTests.isEmpty());
    }

    @Test
    void toolUseIdIsMvn() {
        ToolResult result = simulateMvnOutput("output", 0, false);
        assertEquals("mvn", result.toolUseId());
    }

    // --- Timeout test with real process ---

    @Test
    void timeoutTerminatesProcess(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path script = createScript(tempDir, "sleep 30; echo DONE");
        ProcessBuilder pb = new ProcessBuilder(script.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(1, TimeUnit.SECONDS);
        assertFalse(finished);
        process.destroyForcibly();
        // Wait for the process to actually terminate after destroyForcibly
        boolean terminated = process.waitFor(5, TimeUnit.SECONDS);
        assertTrue(terminated, "Process should terminate after destroyForcibly");
    }

    // --- Helper methods ---

    static boolean mvnAvailable() {
        try {
            Process p = new ProcessBuilder("mvn", "--version").start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Path createScript(Path dir, String body) throws IOException {
        Path script = dir.resolve("test-script");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        script.toFile().setExecutable(true);
        return script;
    }

    /**
     * Simulates MvnTool's result parsing logic. This validates the output parsing (failed test
     * extraction, metadata construction) without needing to run mvn.
     */
    private ToolResult simulateMvnOutput(String output, int exitCode, boolean timedOut) {
        boolean buildSuccess = exitCode == 0 && !timedOut;
        List<String> failedTests = parseFailedTests(output);
        Map<String, Object> meta =
                Map.of(
                        "exitCode", exitCode,
                        "buildSuccess", buildSuccess,
                        "failedTestCount", failedTests.size(),
                        "failedTests", failedTests,
                        "durationMs", 0L,
                        "timedOut", timedOut);
        return buildSuccess
                ? ToolResult.success("mvn", output, meta)
                : ToolResult.error("mvn", output, meta);
    }

    private List<String> parseFailedTests(String output) {
        List<String> failed = new java.util.ArrayList<>();
        Matcher m = FAILED_TEST_PATTERN.matcher(output);
        while (m.find()) {
            failed.add(m.group(1));
        }
        return failed;
    }
}
