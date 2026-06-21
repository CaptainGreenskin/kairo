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

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Runs a verification suite (build/test/check commands) and reports whether the agent's executed
 * plan actually works. Designed to close the Plan → Execute → Verify loop: after the agent finishes
 * the actionable items from {@code exit_plan_mode}, it calls this tool to check that the changes
 * actually compile and pass tests before declaring success.
 *
 * <p>Two modes:
 *
 * <ul>
 *   <li><b>Auto-detect</b> (default, no {@code commands} param) — looks for a known build system
 *       marker in the working directory and runs the canonical test command for it. Detection
 *       order: {@code pom.xml} → {@code mvn -q test}; {@code package.json} → {@code npm test};
 *       {@code pyproject.toml}/{@code pytest.ini} → {@code pytest -q}; {@code Cargo.toml} → {@code
 *       cargo test}; {@code Makefile} with a {@code test:} target → {@code make test}. None matched
 *       → error result telling the agent to pass {@code commands} explicitly.
 *   <li><b>Explicit commands</b> — pass {@code commands} as an array of shell command lines. Each
 *       runs in sequence; non-zero exit fails the verification (or, with {@code failFast=false},
 *       all commands run and the result aggregates per-command status).
 * </ul>
 *
 * <p>Output: a structured report with per-command exit code + duration, captured stdout/stderr
 * tail, and an overall {@code verified} boolean. The agent reads this to decide whether to call
 * {@code todo_write} marking items complete, or to start a fresh repair iteration.
 *
 * @since 1.3
 */
@Tool(
        name = "verify_execution",
        description =
                "Run the project's verification suite (build/tests) to check that the plan's changes"
                        + " actually work. By default, auto-detects the build system from the working"
                        + " directory (pom.xml → mvn test; package.json → npm test; pyproject.toml /"
                        + " pytest.ini → pytest; Cargo.toml → cargo test; Makefile with test target →"
                        + " make test). Pass 'commands' as a string list to override. Returns per-command"
                        + " exit code + output tail and an overall 'verified' boolean. Call this after"
                        + " finishing the actionable items from exit_plan_mode and BEFORE declaring the"
                        + " task done.",
        category = ToolCategory.EXECUTION,
        sideEffect = ToolSideEffect.SYSTEM_CHANGE)
public class VerifyExecutionTool implements SyncTool {

    private static final Logger log = LoggerFactory.getLogger(VerifyExecutionTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 600;
    private static final int MAX_OUTPUT_BYTES_PER_COMMAND = 50_000;

    @ToolParam(
            description =
                    "Verification commands to run, in sequence. Each entry is a single shell command"
                            + " line (e.g. \"mvn -q test\"). When omitted, the tool auto-detects the"
                            + " build system and runs its canonical test command.")
    private List<String> commands;

    @ToolParam(
            description = "Working directory relative to workspace root (default: workspace root)")
    private String workingDir;

    @ToolParam(
            description =
                    "Total timeout in seconds across all commands (default: 600). Each command also"
                            + " gets this as its individual ceiling.")
    private Integer timeoutSeconds;

    @ToolParam(
            description =
                    "Stop on first failing command (default: true). When false, all commands run and"
                            + " the per-command results are aggregated.")
    private Boolean failFast;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> input, ToolContext ctx) {
        Workspace workspace = ctx.workspace();
        Path root = workspace.root();
        String wd = (String) input.get("workingDir");
        Path workDir = (wd == null || wd.isBlank()) ? root : root.resolve(wd).normalize();
        if (!Files.isDirectory(workDir)) {
            return ToolResult.error(null, "Working directory does not exist: " + workDir);
        }

        // Resolve commands: explicit > auto-detected.
        List<String> resolved;
        String detected = null;
        Object cmdsRaw = input.get("commands");
        if (cmdsRaw instanceof List<?> list && !list.isEmpty()) {
            resolved = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof String s && !s.isBlank()) {
                    resolved.add(s.trim());
                }
            }
            if (resolved.isEmpty()) {
                return ToolResult.error(
                        null,
                        "Parameter 'commands' was provided but contained no non-blank entries");
            }
        } else {
            Detection d = autoDetect(workDir);
            if (d == null) {
                return ToolResult.error(
                        null,
                        "No build-system marker found in "
                                + workDir
                                + " (looked for pom.xml, package.json, pyproject.toml, pytest.ini,"
                                + " Cargo.toml, Makefile-with-test-target). Pass 'commands' explicitly"
                                + " with the verification commands for this project.");
            }
            resolved = List.of(d.command);
            detected = d.tooling;
        }

        int totalTimeout =
                (timeoutSeconds(input) > 0) ? timeoutSeconds(input) : DEFAULT_TIMEOUT_SECONDS;
        boolean stopOnFail = !Boolean.FALSE.equals(input.get("failFast"));

        List<Map<String, Object>> commandResults = new ArrayList<>();
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + TimeUnit.SECONDS.toNanos(totalTimeout);
        boolean overallPass = true;
        StringBuilder report = new StringBuilder();
        report.append("Verification report").append('\n');
        if (detected != null) {
            report.append("Auto-detected build system: ").append(detected).append('\n');
        }
        report.append("Working directory: ").append(workDir).append('\n');
        report.append("Commands (").append(resolved.size()).append("):").append('\n');

        for (int i = 0; i < resolved.size(); i++) {
            String cmd = resolved.get(i);
            long remaining = TimeUnit.NANOSECONDS.toSeconds(deadlineNanos - System.nanoTime());
            if (remaining <= 0) {
                report.append(
                        String.format(
                                "  [%d] %s — SKIPPED (total timeout exceeded)%n", i + 1, cmd));
                overallPass = false;
                commandResults.add(
                        commandResult(cmd, -1, 0L, "", "skipped: total timeout exceeded"));
                break;
            }
            CommandRun run = runCommand(cmd, workDir, remaining);
            commandResults.add(
                    commandResult(
                            cmd, run.exitCode, run.durationMs, run.stdoutTail, run.stderrTail));
            boolean pass = run.exitCode == 0;
            report.append(
                    String.format(
                            "  [%d] %s%n      → exit=%d, %dms%n",
                            i + 1, cmd, run.exitCode, run.durationMs));
            if (!run.stdoutTail.isEmpty()) {
                report.append(indent(run.stdoutTail, "      stdout> ")).append('\n');
            }
            if (!run.stderrTail.isEmpty()) {
                report.append(indent(run.stderrTail, "      stderr> ")).append('\n');
            }
            if (!pass) {
                overallPass = false;
                if (stopOnFail) {
                    report.append(
                            "  [stopped on first failure; pass failFast=false to continue]\n");
                    break;
                }
            }
        }

        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        report.append('\n')
                .append(overallPass ? "VERIFIED" : "FAILED")
                .append(" (total ")
                .append(totalMs)
                .append("ms)");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("verified", overallPass);
        metadata.put("totalDurationMs", totalMs);
        metadata.put("commandResults", commandResults);
        if (detected != null) {
            metadata.put("detectedTooling", detected);
        }
        return ToolResult.success(null, report.toString(), metadata);
    }

    private int timeoutSeconds(Map<String, Object> input) {
        Object v = input.get("timeoutSeconds");
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 0;
    }

    /**
     * Detects which build system is present in {@code workDir}. Returns the first match in priority
     * order, or null if nothing matched.
     */
    static Detection autoDetect(Path workDir) {
        if (Files.isRegularFile(workDir.resolve("pom.xml"))) {
            return new Detection("mvn", "mvn -q test");
        }
        if (Files.isRegularFile(workDir.resolve("package.json"))) {
            // We deliberately don't peek at scripts here — npm test is the canonical entry, and if
            // the project doesn't define it npm prints a clear "missing script: test" that the
            // model can act on.
            return new Detection("npm", "npm test");
        }
        if (Files.isRegularFile(workDir.resolve("pyproject.toml"))
                || Files.isRegularFile(workDir.resolve("pytest.ini"))) {
            return new Detection("pytest", "pytest -q");
        }
        if (Files.isRegularFile(workDir.resolve("Cargo.toml"))) {
            return new Detection("cargo", "cargo test");
        }
        Path makefile = workDir.resolve("Makefile");
        if (Files.isRegularFile(makefile)) {
            try {
                String content = Files.readString(makefile, StandardCharsets.UTF_8);
                // Only auto-pick make when there's an obvious test target — avoids running an
                // arbitrary default `make` that might build the world.
                if (content.contains("\ntest:") || content.startsWith("test:")) {
                    return new Detection("make", "make test");
                }
            } catch (IOException ignored) {
                // Makefile exists but unreadable — fall through to "not detected".
            }
        }
        return null;
    }

    private CommandRun runCommand(String cmd, Path workDir, long timeoutSeconds) {
        long start = System.nanoTime();
        try {
            ProcessBuilder pb =
                    new ProcessBuilder(io.kairo.core.util.ShellCommand.buildCommand(cmd))
                            .directory(workDir.toFile());
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            String stdout;
            String stderr;
            try (InputStream out = proc.getInputStream();
                    InputStream err = proc.getErrorStream()) {
                Thread errThread =
                        new Thread(
                                () -> {
                                    try {
                                        readStream(err);
                                    } catch (IOException ignored) {
                                        // Process may have closed early; we'll catch the real exit
                                        // code below.
                                    }
                                });
                errThread.setDaemon(true);
                errThread.start();

                // Drain stdout on this thread; let the err thread drain stderr.
                stdout = readStream(out);
                errThread.join(TimeUnit.SECONDS.toMillis(2));
                // Best-effort stderr capture; if the drain thread hasn't finished, we'll miss the
                // very last bytes — acceptable for a verification report (stderr usually small).
                stderr = "";
                try (InputStream err2 = proc.getErrorStream()) {
                    stderr = readStream(err2);
                } catch (IOException ignored) {
                    // ignored
                }
            }

            boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                long durMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                return new CommandRun(
                        -1, durMs, tailBytes(stdout), "TIMEOUT after " + timeoutSeconds + "s");
            }
            int exit = proc.exitValue();
            long durMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return new CommandRun(exit, durMs, tailBytes(stdout), tailBytes(stderr));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            long durMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return new CommandRun(-1, durMs, "", "execution error: " + e.getMessage());
        }
    }

    private static String readStream(InputStream in) throws IOException {
        byte[] buf = in.readAllBytes();
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static String tailBytes(String s) {
        if (s == null) return "";
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length <= MAX_OUTPUT_BYTES_PER_COMMAND) return s;
        byte[] tail = new byte[MAX_OUTPUT_BYTES_PER_COMMAND];
        System.arraycopy(
                b, b.length - MAX_OUTPUT_BYTES_PER_COMMAND, tail, 0, MAX_OUTPUT_BYTES_PER_COMMAND);
        return "...[truncated "
                + (b.length - MAX_OUTPUT_BYTES_PER_COMMAND)
                + " bytes]...\n"
                + new String(tail, StandardCharsets.UTF_8);
    }

    private static String indent(String text, String prefix) {
        if (text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        int max = Math.min(lines.length, 20); // cap report blob to 20 lines per stream
        for (int i = 0; i < max; i++) {
            sb.append(prefix).append(lines[i]);
            if (i < max - 1) sb.append('\n');
        }
        if (lines.length > max) {
            sb.append('\n')
                    .append(prefix)
                    .append("...[")
                    .append(lines.length - max)
                    .append(" more lines]");
        }
        return sb.toString();
    }

    private static Map<String, Object> commandResult(
            String command, int exitCode, long durationMs, String stdoutTail, String stderrTail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("command", command);
        m.put("exitCode", exitCode);
        m.put("durationMs", durationMs);
        m.put("stdoutTail", stdoutTail);
        m.put("stderrTail", stderrTail);
        return m;
    }

    /**
     * Detected build system descriptor: human-readable {@code tooling} + canonical {@code command}.
     */
    record Detection(String tooling, String command) {}

    private record CommandRun(
            int exitCode, long durationMs, String stdoutTail, String stderrTail) {}
}
