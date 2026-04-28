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
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Maven goals and returns structured build results.
 *
 * <p>Captures stdout+stderr (last {@value #MAX_OUTPUT_BYTES} bytes), parses failed test names from
 * Surefire output, and reports {@code buildSuccess}, {@code exitCode}, and {@code durationMs} in
 * the result metadata.
 *
 * <p>Requires {@code mvn} on {@code PATH}.
 */
@Tool(
        name = "mvn",
        description =
                "Run Maven goals (compile, test, package, etc.) in the workspace and return the build result.",
        category = ToolCategory.EXECUTION,
        sideEffect = ToolSideEffect.SYSTEM_CHANGE)
public class MvnTool implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(MvnTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int MAX_OUTPUT_BYTES = 100_000;

    // Matches lines like: [ERROR] io.kairo.SomeTest.someMethod -- Time elapsed... <<< FAILURE!
    private static final Pattern FAILED_TEST_PATTERN =
            Pattern.compile("\\[ERROR\\]\\s+(\\S+(?:\\.\\S+)+)\\s+--.*<<<\\s+(?:FAILURE|ERROR)");

    @ToolParam(
            description = "Maven goals and flags, e.g. [\"test\", \"-pl\", \"kairo-core\"]",
            required = true)
    private List<String> goals;

    @ToolParam(
            description = "Working directory relative to workspace root (default: workspace root)")
    private String workingDir;

    @ToolParam(description = "Maven profiles to activate, e.g. [\"-Pintegration-tests\"]")
    private List<String> profiles;

    @ToolParam(description = "Skip test execution (passes -DskipTests, default: false)")
    private Boolean skipTests;

    @ToolParam(description = "Timeout in seconds (default: 300)")
    private Integer timeout;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(input, null);
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        return doExecute(input, context);
    }

    @SuppressWarnings("unchecked")
    private ToolResult doExecute(Map<String, Object> input, ToolContext context) {
        List<String> goalList = (List<String>) input.get("goals");
        if (goalList == null || goalList.isEmpty()) {
            return error("Parameter 'goals' is required");
        }

        int timeoutSec = parseIntParam(input.get("timeout"), DEFAULT_TIMEOUT_SECONDS);
        boolean skip = parseBoolParam(input.get("skipTests"), false);
        List<String> profileList = (List<String>) input.getOrDefault("profiles", List.of());

        Path workspaceRoot = context == null ? Workspace.cwd().root() : context.workspace().root();
        Path workDir = resolveWorkDir(input.get("workingDir"), workspaceRoot);
        if (workDir == null) {
            return error("workingDir does not exist: " + input.get("workingDir"));
        }

        List<String> cmd = buildCommand(goalList, profileList, skip);
        log.debug("Running maven: {} in {}", cmd, workDir);

        long startMs = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(workDir.toFile());

            Process process = pb.start();
            byte[] output = drainOutput(process.getInputStream(), MAX_OUTPUT_BYTES);
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);

            long durationMs = System.currentTimeMillis() - startMs;
            if (!finished) {
                process.destroyForcibly();
                String tail = tailBytes(output);
                return new ToolResult(
                        "mvn",
                        "Maven timed out after " + timeoutSec + "s.\n\n" + tail,
                        true,
                        Map.of(
                                "exitCode",
                                -1,
                                "buildSuccess",
                                false,
                                "failedTestCount",
                                0,
                                "durationMs",
                                durationMs,
                                "timedOut",
                                true));
            }

            int exitCode = process.exitValue();
            String outputStr = tailBytes(output);
            boolean buildSuccess = exitCode == 0;
            List<String> failedTests = parseFailedTests(outputStr);

            return new ToolResult(
                    "mvn",
                    outputStr,
                    !buildSuccess,
                    Map.of(
                            "exitCode", exitCode,
                            "buildSuccess", buildSuccess,
                            "failedTestCount", failedTests.size(),
                            "failedTests", failedTests,
                            "durationMs", durationMs));

        } catch (IOException e) {
            return error("Failed to start mvn: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("Maven execution interrupted");
        }
    }

    private static List<String> buildCommand(
            List<String> goals, List<String> profiles, boolean skipTests) {
        List<String> cmd = new ArrayList<>();
        cmd.add("mvn");
        cmd.addAll(goals);
        cmd.addAll(profiles);
        if (skipTests) {
            cmd.add("-DskipTests");
        }
        return cmd;
    }

    /**
     * Reads all bytes from the stream, keeping at most {@code maxBytes} (dropping leading bytes).
     */
    private static byte[] drainOutput(InputStream in, int maxBytes) throws IOException {
        byte[] ring = new byte[maxBytes];
        int pos = 0;
        boolean wrapped = false;
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            for (int i = 0; i < n; i++) {
                ring[pos] = buf[i];
                pos++;
                if (pos == maxBytes) {
                    pos = 0;
                    wrapped = true;
                }
            }
        }
        if (!wrapped) {
            byte[] result = new byte[pos];
            System.arraycopy(ring, 0, result, 0, pos);
            return result;
        }
        // reassemble ring: [pos..end] + [0..pos)
        byte[] result = new byte[maxBytes];
        System.arraycopy(ring, pos, result, 0, maxBytes - pos);
        System.arraycopy(ring, 0, result, maxBytes - pos, pos);
        return result;
    }

    private static String tailBytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<String> parseFailedTests(String output) {
        List<String> failed = new ArrayList<>();
        Matcher m = FAILED_TEST_PATTERN.matcher(output);
        while (m.find()) {
            failed.add(m.group(1));
        }
        return failed;
    }

    private static Path resolveWorkDir(Object workingDirParam, Path workspaceRoot) {
        if (workingDirParam instanceof String s && !s.isBlank()) {
            Path resolved = workspaceRoot.resolve(s).normalize();
            return Files.isDirectory(resolved) ? resolved : null;
        }
        return workspaceRoot;
    }

    private static int parseIntParam(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    private static boolean parseBoolParam(Object val, boolean defaultVal) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }

    private ToolResult error(String msg) {
        return new ToolResult("mvn", msg, true, Map.of("buildSuccess", false));
    }
}
