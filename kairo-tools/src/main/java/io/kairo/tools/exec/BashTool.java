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

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.tool.ToolHandler;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a shell command and returns its output.
 *
 * <p>Uses {@link ProcessBuilder} to run commands with configurable timeout and working directory.
 * Captures both stdout and stderr.
 */
@Tool(
        name = "bash",
        description =
                "Execute a shell command and return its output. Use for running programs, installing packages, or system operations.",
        category = ToolCategory.EXECUTION,
        sideEffect = ToolSideEffect.SYSTEM_CHANGE)
public class BashTool implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int MAX_OUTPUT_CHARS = 100_000;

    @ToolParam(description = "The shell command to execute", required = true)
    private String command;

    @ToolParam(description = "Timeout in seconds (default: 120)")
    private Integer timeout;

    @ToolParam(description = "Working directory for the command")
    private String workingDirectory;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String cmd = (String) input.get("command");
        if (cmd == null || cmd.isBlank()) {
            return error("Parameter 'command' is required");
        }

        int timeoutSec = DEFAULT_TIMEOUT_SECONDS;
        Object timeoutObj = input.get("timeout");
        if (timeoutObj instanceof Number n) {
            timeoutSec = n.intValue();
        } else if (timeoutObj instanceof String s) {
            try {
                timeoutSec = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }

        String workDir = (String) input.get("workingDirectory");

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", cmd);
            pb.redirectErrorStream(true);
            if (workDir != null && !workDir.isBlank()) {
                File wd = new File(workDir);
                if (wd.isDirectory()) {
                    pb.directory(wd);
                } else {
                    return error("Working directory does not exist: " + workDir);
                }
            }

            log.debug("Executing command: {}", cmd);
            Process process = pb.start();

            // Schedule a watchdog to kill the process on timeout.
            // This ensures the timeout is enforced even while we block reading output.
            AtomicBoolean timedOut = new AtomicBoolean(false);
            ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
            watchdog.schedule(
                    () -> {
                        if (process.isAlive()) {
                            timedOut.set(true);
                            process.destroyForcibly();
                        }
                    },
                    timeoutSec,
                    TimeUnit.SECONDS);

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        output.append(line).append('\n');
                    }
                }
            } finally {
                watchdog.shutdownNow();
            }

            process.waitFor(5, TimeUnit.SECONDS);

            if (timedOut.get()) {
                return new ToolResult(
                        "bash",
                        "Command timed out after " + timeoutSec + "s.\nPartial output:\n" + output,
                        true,
                        Map.of("exitCode", -1, "timedOut", true));
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString();
            if (outputStr.length() >= MAX_OUTPUT_CHARS) {
                outputStr += "\n... (output truncated at " + MAX_OUTPUT_CHARS + " characters)";
            }

            boolean isError = exitCode != 0;
            return new ToolResult(
                    "bash", outputStr, isError, Map.of("exitCode", exitCode, "command", cmd));

        } catch (Exception e) {
            log.error("Command execution failed: {}", cmd, e);
            return error("Failed to execute command: " + e.getMessage());
        }
    }

    private ToolResult error(String msg) {
        return new ToolResult("bash", msg, true, Map.of());
    }
}
