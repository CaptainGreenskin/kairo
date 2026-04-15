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

import io.kairo.api.tool.ToolResult;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashToolTest {

    private BashTool tool;

    @BeforeEach
    void setUp() {
        tool = new BashTool();
    }

    @Test
    void executeEchoCommand() {
        ToolResult result = tool.execute(Map.of("command", "echo hello"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("hello"));
    }

    @Test
    void executeCommandWithExitCode() {
        ToolResult result = tool.execute(Map.of("command", "echo hello"));
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    void executeFailingCommand() {
        ToolResult result = tool.execute(Map.of("command", "exit 1"));
        assertTrue(result.isError());
        assertEquals(1, result.metadata().get("exitCode"));
    }

    @Test
    void executeMissingCommandParameter() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'command' is required"));
    }

    @Test
    void executeBlankCommandParameter() {
        ToolResult result = tool.execute(Map.of("command", "   "));
        assertTrue(result.isError());
    }

    @Test
    void executeWithWorkingDirectory(@TempDir Path tempDir) {
        ToolResult result =
                tool.execute(Map.of("command", "pwd", "workingDirectory", tempDir.toString()));
        assertFalse(result.isError());
        assertTrue(result.content().trim().contains(tempDir.getFileName().toString()));
    }

    @Test
    void executeWithInvalidWorkingDirectory() {
        ToolResult result =
                tool.execute(
                        Map.of("command", "echo test", "workingDirectory", "/nonexistent/path"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Working directory does not exist"));
    }

    @Test
    void executeWithTimeoutParamAccepted() {
        // BashTool reads all output before checking timeout, so the timeout only
        // triggers if the process produces output and THEN hangs. With a fast
        // command, the timeout param is parsed but not triggered.
        ToolResult result = tool.execute(Map.of("command", "echo fast", "timeout", 5));
        assertFalse(result.isError());
        assertTrue(result.content().contains("fast"));
    }

    @Test
    void executeMultiLineOutput() {
        ToolResult result = tool.execute(Map.of("command", "echo line1 && echo line2"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("line1"));
        assertTrue(result.content().contains("line2"));
    }

    @Test
    void executeWithStringTimeout() {
        ToolResult result = tool.execute(Map.of("command", "echo ok", "timeout", "60"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("ok"));
    }
}
