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
package io.kairo.tools.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolResult;
import io.kairo.tools.exec.BashTool;
import io.kairo.tools.exec.MonitorTool;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for execution tools (BashTool, MonitorTool) that actually execute system
 * commands. These tests verify real process execution, timeout handling, output capture, and
 * working directory behavior.
 */
@Tag("integration")
class ExecutionToolsIntegrationIT {

    private BashTool bashTool;
    private MonitorTool monitorTool;

    @BeforeEach
    void setUp() {
        bashTool = new BashTool();
        monitorTool = new MonitorTool();
    }

    // ── BashTool tests ──────────────────────────────────────────────────

    @Test
    void bashTool_simpleCommand_returnsOutput() {
        ToolResult result = bashTool.execute(Map.of("command", "echo hello"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("hello"));
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    void bashTool_commandWithArgs_returnsOutput() {
        ToolResult result = bashTool.execute(Map.of("command", "printf '%s %s' foo bar"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("foo bar"));
    }

    @Test
    void bashTool_commandFails_returnsError() {
        ToolResult result = bashTool.execute(Map.of("command", "exit 42"));

        assertTrue(result.isError());
        assertEquals(42, result.metadata().get("exitCode"));
    }

    @Test
    void bashTool_commandTimeout_returnsError() {
        // Use a very short timeout (1 second) with a long-running command
        ToolResult result = bashTool.execute(Map.of("command", "sleep 30", "timeout", 1));

        assertTrue(result.isError());
        assertTrue(result.content().contains("timed out"));
        assertEquals(-1, result.metadata().get("exitCode"));
        assertEquals(true, result.metadata().get("timedOut"));
    }

    @Test
    void bashTool_longOutput_handledCorrectly() {
        // Generate many lines of output
        ToolResult result =
                bashTool.execute(Map.of("command", "seq 1 5000"));

        assertFalse(result.isError());
        // Should contain first and last lines
        assertTrue(result.content().contains("1\n"));
        assertTrue(result.content().contains("5000"));
    }

    @Test
    void bashTool_stderrCaptured() {
        // BashTool uses redirectErrorStream(true), so stderr merges into stdout
        ToolResult result =
                bashTool.execute(Map.of("command", "echo error_msg >&2"));

        // stderr is redirected to stdout, so content should contain it
        assertTrue(result.content().contains("error_msg"));
    }

    @Test
    void bashTool_workingDirectory_respected(@TempDir Path tempDir) {
        ToolResult result =
                bashTool.execute(
                        Map.of("command", "pwd", "workingDirectory", tempDir.toString()));

        assertFalse(result.isError());
        assertTrue(result.content().trim().contains(tempDir.getFileName().toString()));
    }

    @Test
    void bashTool_environmentVariables_inherited() {
        // System environment variables should be accessible
        ToolResult result = bashTool.execute(Map.of("command", "echo $HOME"));

        assertFalse(result.isError());
        String output = result.content().trim();
        // HOME should be set and non-empty
        assertFalse(output.isEmpty());
        assertTrue(output.startsWith("/"));
    }

    // ── MonitorTool tests ───────────────────────────────────────────────

    @Test
    void monitorTool_missingTarget_returnsError() {
        ToolResult result = monitorTool.execute(Map.of());

        assertTrue(result.isError());
        assertTrue(result.content().contains("'target' is required"));
    }

    @Test
    void monitorTool_nonNumericTarget_returnsError() {
        ToolResult result = monitorTool.execute(Map.of("target", "rm -rf /"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("numeric PID"));
    }

    @Test
    void monitorTool_validPid_returnsOutput() {
        // Monitor the current JVM process — should always exist
        String pid = String.valueOf(ProcessHandle.current().pid());
        ToolResult result = monitorTool.execute(Map.of("target", pid));

        assertFalse(result.isError());
        // ps output should contain the PID
        assertTrue(result.content().contains(pid));
    }

    @Test
    void monitorTool_nonExistentPid_handledGracefully() {
        // Use an unlikely PID
        ToolResult result = monitorTool.execute(Map.of("target", "9999999"));

        // Should not throw — either returns empty output or error info
        assertNotNull(result.content());
    }
}
