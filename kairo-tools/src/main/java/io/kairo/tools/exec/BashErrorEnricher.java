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

import io.kairo.api.tool.Hint;
import io.kairo.api.tool.Hint.HintLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enriches bash tool error output with actionable hints for the agent.
 *
 * <p>Detects common error patterns (BSD/GNU flag incompatibilities, missing commands, permission
 * issues) and produces {@link Hint} instances that guide the agent toward a fix without requiring
 * another LLM round-trip.
 *
 * @since 1.2.0
 */
public final class BashErrorEnricher {

    private static final boolean IS_MAC =
            System.getProperty("os.name", "").toLowerCase().contains("mac");

    private static final List<ErrorPattern> PATTERNS =
            List.of(
                    new ErrorPattern(
                            "illegal option",
                            HintLevel.WARNING,
                            "macOS uses BSD tools with different flags",
                            Map.of(
                                    "-A", "cat -e",
                                    "-P", "grep -E",
                                    "--color=auto", "(omit on BSD)")),
                    new ErrorPattern(
                            "command not found",
                            HintLevel.ERROR,
                            "Command not available on this system",
                            null),
                    new ErrorPattern(
                            "No such file or directory",
                            HintLevel.ERROR,
                            "File or directory does not exist",
                            null),
                    new ErrorPattern(
                            "Permission denied",
                            HintLevel.ERROR,
                            "Insufficient permissions",
                            null));

    private BashErrorEnricher() {}

    /**
     * Analyze command output and exit status to produce actionable hints.
     *
     * @param output the combined stdout/stderr output
     * @param exitCode the process exit code
     * @param timedOut whether the process was killed due to timeout
     * @return a list of hints (may be empty for successful executions)
     */
    public static List<Hint> enrich(String output, int exitCode, boolean timedOut) {
        List<Hint> hints = new ArrayList<>();

        if (timedOut) {
            hints.add(
                    new Hint(
                            HintLevel.INFO,
                            "Command timed out",
                            Optional.of(
                                    "Break into smaller steps or increase timeout via"
                                            + " KAIRO_TOOL_TIMEOUT_MS")));
            return hints;
        }

        if (exitCode == 0) return hints;

        for (ErrorPattern pattern : PATTERNS) {
            if (output.contains(pattern.marker)) {
                String fix = pattern.buildFix(output);
                hints.add(new Hint(pattern.level, pattern.message, Optional.ofNullable(fix)));
                break; // one hint per error is enough
            }
        }

        // BSD/GNU specific hints
        if (IS_MAC && exitCode != 0) {
            if (output.contains("grep") && output.contains("-P")) {
                hints.add(
                        new Hint(
                                HintLevel.WARNING,
                                "PCRE not available in BSD grep",
                                Optional.of(
                                        "Use grep -E for extended regex, or install ggrep via:"
                                                + " brew install grep")));
            }
            if (output.contains("sed")
                    && output.contains("-i")
                    && output.contains("invalid command")) {
                hints.add(
                        new Hint(
                                HintLevel.WARNING,
                                "BSD sed requires extension argument with -i",
                                Optional.of(
                                        "Use sed -i '' on macOS (empty string for no backup)")));
            }
        }

        return hints;
    }

    private record ErrorPattern(
            String marker, HintLevel level, String message, Map<String, String> flagAlternatives) {
        String buildFix(String output) {
            if (flagAlternatives == null) return null;
            for (var entry : flagAlternatives.entrySet()) {
                if (output.contains(entry.getKey())) {
                    return "Try: " + entry.getValue() + " instead of " + entry.getKey();
                }
            }
            return null;
        }
    }
}
