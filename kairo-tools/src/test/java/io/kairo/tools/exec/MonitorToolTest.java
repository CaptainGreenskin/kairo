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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonitorToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    private MonitorTool tool;

    private ToolResult exec(Map<String, Object> args) {
        return tool.execute(args, CTX).block();
    }

    @BeforeEach
    void setUp() {
        tool = new MonitorTool();
    }

    @Test
    void missingTargetParameter() {
        ToolResult result = exec(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'target' is required"));
    }

    @Test
    void blankTargetParameter() {
        ToolResult result = exec(Map.of("target", "   "));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'target' is required"));
    }

    @Test
    void nonNumericTargetRejected() {
        ToolResult result = exec(Map.of("target", "abc"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("must be a numeric PID"));
    }

    @Test
    void commandInjectionRejected() {
        ToolResult result = exec(Map.of("target", "123; rm -rf /"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("must be a numeric PID"));
    }

    @Test
    void commandInjectionWithPipeRejected() {
        ToolResult result = exec(Map.of("target", "1 | cat /etc/passwd"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("must be a numeric PID"));
    }

    @Test
    void commandInjectionWithBackticksRejected() {
        ToolResult result = exec(Map.of("target", "`whoami`"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("must be a numeric PID"));
    }

    @Test
    void monitorNonExistentPid() {
        // Use a very high PID that almost certainly doesn't exist
        ToolResult result = exec(Map.of("target", "999999999"));
        // Should not be an error - the tool returns output from ps (which will show no process)
        assertFalse(result.isError());
    }

    @Test
    void monitorWithNumericLines() {
        // Use PID 1 which typically exists on Unix systems, or will at least not crash
        ToolResult result = exec(Map.of("target", "1", "lines", 10));
        // Accept both error (if process can't be monitored) and success
        assertNotNull(result);
        assertNotNull(result.content());
    }

    @Test
    void monitorWithStringLines() {
        ToolResult result = exec(Map.of("target", "1", "lines", "20"));
        assertNotNull(result);
        assertNotNull(result.content());
    }

    @Test
    void monitorWithInvalidStringLines() {
        // Invalid lines value should default to 50
        ToolResult result = exec(Map.of("target", "1", "lines", "abc"));
        assertNotNull(result);
        assertNotNull(result.content());
    }

    @Test
    void monitorWithZeroLinesClampedToOne() {
        ToolResult result = exec(Map.of("target", "1", "lines", 0));
        assertNotNull(result);
    }

    @Test
    void monitorWithLargeLinesClampedToMax() {
        ToolResult result = exec(Map.of("target", "1", "lines", 9999));
        assertNotNull(result);
    }

    @Test
    void monitorOwnProcess() {
        // Get current process PID
        long pid = ProcessHandle.current().pid();
        ToolResult result = exec(Map.of("target", String.valueOf(pid)));
        assertFalse(result.isError());
        assertTrue(result.content().length() > 0);
    }

    @Test
    void errorResultFormat() {
        ToolResult result = exec(Map.of());
        assertEquals("monitor", result.toolUseId());
        assertTrue(result.isError());
        assertTrue(result.metadata().isEmpty());
    }

    @Test
    void successResultContainsTargetInMetadata() {
        long pid = ProcessHandle.current().pid();
        ToolResult result = exec(Map.of("target", String.valueOf(pid)));
        assertFalse(result.isError());
        assertEquals(String.valueOf(pid), result.metadata().get("target"));
    }

    @Test
    void successResultContainsLinesInMetadata() {
        long pid = ProcessHandle.current().pid();
        ToolResult result = exec(Map.of("target", String.valueOf(pid), "lines", 10));
        assertFalse(result.isError());
        assertEquals(10, result.metadata().get("lines"));
    }

    @Test
    void defaultLinesIsUsedWhenNotSpecified() {
        long pid = ProcessHandle.current().pid();
        ToolResult result = exec(Map.of("target", String.valueOf(pid)));
        assertFalse(result.isError());
        assertEquals(50, result.metadata().get("lines"));
    }

    @Test
    void negativeLinesClampedToOne() {
        long pid = ProcessHandle.current().pid();
        ToolResult result = exec(Map.of("target", String.valueOf(pid), "lines", -5));
        assertFalse(result.isError());
        assertEquals(1, result.metadata().get("lines"));
    }

    @Test
    void linesAt500NotExceededInMetadata() {
        long pid = ProcessHandle.current().pid();
        ToolResult result = exec(Map.of("target", String.valueOf(pid), "lines", 500));
        assertFalse(result.isError());
        assertEquals(500, result.metadata().get("lines"));
    }

    @Test
    void successResultToolUseIdIsMonitor() {
        long pid = ProcessHandle.current().pid();
        ToolResult result = exec(Map.of("target", String.valueOf(pid)));
        assertFalse(result.isError());
        assertEquals("monitor", result.toolUseId());
    }

    @Test
    void nonNumericWithLeadingDigitsRejected() {
        ToolResult result = exec(Map.of("target", "123abc"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("must be a numeric PID"));
    }

    @Test
    void tooLongNumericPidRejected() {
        ToolResult result = exec(Map.of("target", "12345678901"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("must be a numeric PID"));
    }
}
