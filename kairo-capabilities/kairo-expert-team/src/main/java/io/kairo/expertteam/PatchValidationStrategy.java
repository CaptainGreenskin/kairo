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
package io.kairo.expertteam;

import io.kairo.api.team.EvaluationContext;
import io.kairo.api.team.EvaluationStrategy;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Evaluation strategy that validates actual filesystem changes via {@code git diff}.
 *
 * <p>Instead of checking whether the agent produced non-blank text (which always passes), this
 * strategy inspects the workspace to verify that real code changes were made. This closes the
 * "empty evaluation" gap that allowed agents to claim success without producing any patch.
 *
 * <p>The rubric:
 *
 * <ul>
 *   <li>Non-empty git diff → {@link VerdictOutcome#PASS} with diff summary as feedback.
 *   <li>Empty git diff → {@link VerdictOutcome#REVISE} with actionable feedback directing the agent
 *       to edit source files.
 *   <li>Git command failure → {@link VerdictOutcome#REVIEW_EXCEEDED} (never a silent PASS per
 *       ADR-015).
 * </ul>
 *
 * @since v0.10 (Experimental)
 */
public final class PatchValidationStrategy implements EvaluationStrategy {

    private static final Logger log = LoggerFactory.getLogger(PatchValidationStrategy.class);
    private static final int GIT_TIMEOUT_SECONDS = 10;

    private final Path workspace;
    private final int gitTimeoutSeconds;

    /** Create with default git timeout (10s). */
    public PatchValidationStrategy(Path workspace) {
        this(workspace, GIT_TIMEOUT_SECONDS);
    }

    /** Create with custom git timeout. */
    public PatchValidationStrategy(Path workspace, int gitTimeoutSeconds) {
        this.workspace = Objects.requireNonNull(workspace, "workspace must not be null");
        this.gitTimeoutSeconds = gitTimeoutSeconds;
    }

    @Override
    public Mono<EvaluationVerdict> evaluate(EvaluationContext context) {
        Objects.requireNonNull(context, "context must not be null");
        return Mono.fromCallable(() -> runGitDiff())
                .map(diffResult -> buildVerdict(diffResult, context.attemptNumber()));
    }

    private DiffResult runGitDiff() {
        ProcessBuilder pb = new ProcessBuilder("git", "diff", "--stat");
        pb.directory(workspace.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("LANG", "C");

        try {
            Process process = pb.start();
            String output;
            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean exited = process.waitFor(gitTimeoutSeconds, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                log.warn("git diff timed out after {}s in {}", gitTimeoutSeconds, workspace);
                return new DiffResult(false, "", "git diff timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("git diff failed (exit={}) in {}: {}", exitCode, workspace, output);
                return new DiffResult(false, output, "git diff exited with code " + exitCode);
            }

            return new DiffResult(true, output, null);
        } catch (IOException | InterruptedException e) {
            log.warn("git diff error in {}: {}", workspace, e.toString());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new DiffResult(false, "", e.getMessage());
        }
    }

    private EvaluationVerdict buildVerdict(DiffResult diffResult, int attemptNumber) {
        if (!diffResult.success()) {
            return new EvaluationVerdict(
                    VerdictOutcome.REVIEW_EXCEEDED,
                    0.0,
                    "Git validation failed: "
                            + diffResult.errorMessage()
                            + " (attempt "
                            + attemptNumber
                            + ")",
                    List.of("Check that the workspace is a valid git repository"),
                    Instant.now());
        }

        String diffStat = diffResult.output().trim();
        if (diffStat.isEmpty()) {
            return new EvaluationVerdict(
                    VerdictOutcome.REVISE,
                    0.0,
                    "No code changes detected in workspace (git diff is empty, attempt "
                            + attemptNumber
                            + "). You must edit source files to fix the bug.",
                    List.of(
                            "Use the edit/write tool to modify source files",
                            "Make sure you are editing the correct file path",
                            "Check that your changes are saved to the workspace"),
                    Instant.now());
        }

        String summary = summariseDiffStat(diffStat);
        return new EvaluationVerdict(
                VerdictOutcome.PASS,
                1.0,
                "Patch detected: " + summary + " (attempt " + attemptNumber + ")",
                List.of(),
                Instant.now());
    }

    private static String summariseDiffStat(String diffStat) {
        String[] lines = diffStat.split("\n");
        if (lines.length == 0) {
            return "changes detected";
        }

        // Last line usually has the summary: "N files changed, M insertions(+), D deletions(-)"
        String lastLine = lines[lines.length - 1].trim();
        if (lastLine.contains("files changed") || lastLine.contains("file changed")) {
            return lastLine;
        }

        // Fallback: count lines
        int fileCount = 0;
        for (String line : lines) {
            if (line.contains("|")) {
                fileCount++;
            }
        }
        return fileCount + " file(s) changed";
    }

    private record DiffResult(boolean success, String output, String errorMessage) {}

    /** Exposed for testing — returns the configured workspace. */
    Path workspace() {
        return workspace;
    }
}
