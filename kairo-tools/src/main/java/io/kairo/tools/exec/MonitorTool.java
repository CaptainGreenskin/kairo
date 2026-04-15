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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors a long-running process by PID and returns its recent output.
 *
 * <p>Only accepts numeric PIDs to prevent command injection. Returns the last N lines of output
 * (default: 50).
 */
@Tool(
        name = "monitor",
        description =
                "Monitor a long-running process by PID and return its recent output. Only accepts numeric PIDs.",
        category = ToolCategory.EXECUTION,
        sideEffect = ToolSideEffect.SYSTEM_CHANGE)
public class MonitorTool implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorTool.class);
    private static final int DEFAULT_LINES = 50;
    private static final int MAX_LINES = 500;

    @ToolParam(description = "Process ID (numeric PID) to monitor", required = true)
    private String target;

    @ToolParam(description = "Number of lines to return (default: 50, max: 500)")
    private Integer lines;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        String target = (String) input.get("target");
        if (target == null || target.isBlank()) {
            return error("Parameter 'target' is required");
        }

        // Security: only accept numeric PIDs to prevent command injection
        if (!target.matches("\\d{1,10}")) {
            return error(
                    "Parameter 'target' must be a numeric PID. "
                            + "Arbitrary command execution is not supported by this tool. "
                            + "Use the 'bash' tool instead.");
        }

        int numLines = DEFAULT_LINES;
        Object linesObj = input.get("lines");
        if (linesObj instanceof Number n) {
            numLines = Math.min(Math.max(1, n.intValue()), MAX_LINES);
        } else if (linesObj instanceof String s) {
            try {
                numLines = Math.min(Math.max(1, Integer.parseInt(s)), MAX_LINES);
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            // Safe: target is guaranteed to be numeric-only at this point
            String command =
                    "ps -p "
                            + target
                            + " -o pid,ppid,stat,time,command 2>/dev/null && echo '---LOGS---' && "
                            + "if [ -f /proc/"
                            + target
                            + "/fd/1 ]; then tail -n "
                            + numLines
                            + " /proc/"
                            + target
                            + "/fd/1 2>/dev/null; "
                            + "else echo 'No stdout available for PID "
                            + target
                            + "'; fi";

            ProcessBuilder pb = new ProcessBuilder("/bin/sh", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }

            String outputStr = output.toString();
            if (outputStr.isBlank()) {
                return new ToolResult(
                        "monitor",
                        "No output available for: " + target,
                        false,
                        Map.of("target", target));
            }

            return new ToolResult(
                    "monitor", outputStr, false, Map.of("target", target, "lines", numLines));

        } catch (Exception e) {
            log.error("Monitor failed for target: {}", target, e);
            return error("Failed to monitor: " + e.getMessage());
        }
    }

    private ToolResult error(String msg) {
        return new ToolResult("monitor", msg, true, Map.of());
    }
}
