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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a git subcommand in the workspace directory.
 *
 * <p>Destructive and irreversible operations (force-push, hard reset, etc.) are blocked to prevent
 * accidental data loss.
 */
@Tool(
        name = "git",
        description =
                "Execute a git command in the workspace. Example: \"status\", \"diff\","
                        + " \"log --oneline -10\", \"add -A\", \"commit -m 'msg'\"."
                        + " Destructive operations (push --force, reset --hard, clean -f) are blocked.",
        category = ToolCategory.EXECUTION,
        sideEffect = ToolSideEffect.WRITE)
public class GitTool implements ToolHandler {

    private static final Logger log = LoggerFactory.getLogger(GitTool.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final long MAX_OUTPUT_BYTES = 200_000L;

    /** Patterns that indicate dangerous, irreversible git operations. */
    private static final List<String> BLOCKED_PATTERNS =
            List.of(
                    "push --force",
                    "push -f ",
                    "push -f\t",
                    "reset --hard",
                    "clean -f",
                    "clean -fd",
                    "branch -D ",
                    "branch -d ",
                    "checkout --",
                    "update-ref -d");

    private final Path overrideWorkspace;

    /** Default constructor — uses workspace or JVM cwd. */
    public GitTool() {
        this(null);
    }

    /** Constructor for testing with an explicit working directory. */
    GitTool(Path overrideWorkspace) {
        this.overrideWorkspace = overrideWorkspace;
    }

    @ToolParam(
            description =
                    "The git subcommand to execute (without the leading 'git'). "
                            + "Examples: \"status\", \"diff\", \"log --oneline -10\"",
            required = true)
    private String subcommand;

    @ToolParam(description = "Working directory override (defaults to workspace root)")
    private String workingDirectory;

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(
                input, overrideWorkspace != null ? overrideWorkspace : Workspace.cwd().root());
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        Path base = overrideWorkspace != null ? overrideWorkspace : context.workspace().root();
        return doExecute(input, base);
    }

    private ToolResult doExecute(Map<String, Object> input, Path defaultRoot) {
        String sub = (String) input.get("subcommand");
        if (sub == null || sub.isBlank()) {
            return error("Parameter 'subcommand' is required");
        }

        String blocked = blockedPattern(sub);
        if (blocked != null) {
            return error(
                    "Blocked dangerous git operation: '"
                            + blocked
                            + "'. Use a safer alternative or perform manually.");
        }

        Path workDir = resolveWorkDir(input.get("workingDirectory"), defaultRoot);
        if (workDir == null || !Files.isDirectory(workDir)) {
            return error("Working directory does not exist: " + input.get("workingDirectory"));
        }

        String fullCommand = "git " + sub;
        log.debug("Executing: {}", fullCommand);

        try {
            ProcessBuilder pb =
                    new ProcessBuilder("/bin/sh", "-c", fullCommand)
                            .directory(workDir.toFile())
                            .redirectErrorStream(true);

            Process process = pb.start();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            try (var in = process.getInputStream()) {
                while ((read = in.read(chunk)) != -1) {
                    if (buffer.size() + read <= MAX_OUTPUT_BYTES) {
                        buffer.write(chunk, 0, read);
                    }
                }
            }

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return error("git command timed out after " + DEFAULT_TIMEOUT_SECONDS + "s");
            }

            int exitCode = process.exitValue();
            String output = buffer.toString(StandardCharsets.UTF_8);
            if (buffer.size() >= MAX_OUTPUT_BYTES) {
                output += "\n... (output truncated at " + MAX_OUTPUT_BYTES + " bytes)";
            }

            boolean isError = exitCode != 0;
            return new ToolResult(
                    "git", output, isError, Map.of("exitCode", exitCode, "subcommand", sub));

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Failed to run git command: {}", sub, e);
            return error("Failed to execute git command: " + e.getMessage());
        }
    }

    private static String blockedPattern(String subcommand) {
        String lower = subcommand.toLowerCase();
        for (String pattern : BLOCKED_PATTERNS) {
            if (lower.contains(pattern)) {
                return pattern;
            }
        }
        return null;
    }

    private static Path resolveWorkDir(Object explicitDir, Path defaultRoot) {
        if (explicitDir instanceof String s && !s.isBlank()) {
            Path explicit = Path.of(s);
            return Files.isDirectory(explicit) ? explicit : null;
        }
        return defaultRoot;
    }

    private ToolResult error(String msg) {
        return new ToolResult("git", msg, true, Map.of());
    }
}
