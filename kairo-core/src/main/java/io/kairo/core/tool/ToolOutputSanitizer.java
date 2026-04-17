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
package io.kairo.core.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scans tool output for potential injection attacks, credential leaks, and suspicious Unicode
 * characters.
 *
 * <p>This sanitizer is designed to be used as a post-execution hook: it inspects the textual
 * output of a tool and returns a {@link ScanResult} containing any warnings found. It does
 * <b>not</b> block or modify the output — warnings are purely informational metadata that
 * downstream consumers can act on.
 *
 * <h3>Detection categories</h3>
 * <ul>
 *   <li><b>Prompt injection phrases</b> — common phrases used in prompt injection attacks</li>
 *   <li><b>System prompt override attempts</b> — tokens or phrases that attempt to override
 *       system-level prompts</li>
 *   <li><b>Invisible Unicode characters</b> — zero-width characters and bidirectional overrides
 *       that can hide malicious content</li>
 *   <li><b>Credential patterns</b> — API keys, AWS access keys, and bearer tokens</li>
 * </ul>
 */
public class ToolOutputSanitizer {

    // ── Prompt injection phrases (case-insensitive) ──
    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore\\s+all\\s+prior\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+above", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+your\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new\\s+system\\s+prompt", Pattern.CASE_INSENSITIVE));

    // ── System prompt override patterns ──
    private static final List<Pattern> SYSTEM_OVERRIDE_PATTERNS = List.of(
            Pattern.compile("system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system\\s+message\\s+override", Pattern.CASE_INSENSITIVE),
            Pattern.compile(Pattern.quote("<|system|>")),
            Pattern.compile(Pattern.quote("<|im_start|>") + "system", Pattern.CASE_INSENSITIVE));

    // ── Credential patterns ──
    private static final List<Pattern> CREDENTIAL_PATTERNS = List.of(
            Pattern.compile(
                    "(sk|ak|key|token|secret|password)[-_]?[a-zA-Z0-9]{20,}",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*", Pattern.CASE_INSENSITIVE));

    // ── Invisible Unicode codepoints to detect ──
    private static final Map<Integer, String> INVISIBLE_CHARS = Map.of(
            0x200B, "ZERO WIDTH SPACE",
            0x200C, "ZERO WIDTH NON-JOINER",
            0x200D, "ZERO WIDTH JOINER",
            0x202D, "LEFT-TO-RIGHT OVERRIDE",
            0x202E, "RIGHT-TO-LEFT OVERRIDE");

    /**
     * Result of scanning tool output.
     *
     * @param warnings list of human-readable warning messages; empty if output is clean
     */
    public record ScanResult(List<String> warnings) {

        /**
         * Returns {@code true} if the scan produced at least one warning.
         *
         * @return whether warnings were found
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    private ToolOutputSanitizer() {
        // utility class
    }

    /**
     * Scan the given tool output for potential injection attacks, credential leaks, and suspicious
     * Unicode characters.
     *
     * <p>The scan is regex-based and designed to be fast. It does not perform heavy parsing.
     *
     * @param toolOutput the raw textual output from a tool execution; may be {@code null}
     * @return a {@link ScanResult} containing any warnings found (never {@code null})
     */
    public static ScanResult scan(String toolOutput) {
        if (toolOutput == null || toolOutput.isEmpty()) {
            return new ScanResult(Collections.emptyList());
        }

        List<String> warnings = new ArrayList<>();

        // 1. Prompt injection phrases
        for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
            var matcher = pattern.matcher(toolOutput);
            if (matcher.find()) {
                warnings.add("Prompt injection phrase detected: \"" + matcher.group() + "\"");
            }
        }

        // 2. System prompt override
        for (Pattern pattern : SYSTEM_OVERRIDE_PATTERNS) {
            var matcher = pattern.matcher(toolOutput);
            if (matcher.find()) {
                warnings.add("System prompt override attempt detected: \"" + matcher.group() + "\"");
            }
        }

        // 3. Invisible Unicode characters
        for (int i = 0; i < toolOutput.length(); i++) {
            int codePoint = toolOutput.codePointAt(i);
            String name = INVISIBLE_CHARS.get(codePoint);
            if (name != null) {
                warnings.add(String.format(
                        "Invisible Unicode character U+%04X (%s) at offset %d", codePoint, name, i));
            }
            if (Character.isSupplementaryCodePoint(codePoint)) {
                i++; // skip surrogate pair
            }
        }

        // 4. Credential patterns
        for (Pattern pattern : CREDENTIAL_PATTERNS) {
            var matcher = pattern.matcher(toolOutput);
            if (matcher.find()) {
                warnings.add("Potential credential leak detected: pattern matches \""
                        + truncate(matcher.group(), 30) + "\"");
            }
        }

        return new ScanResult(Collections.unmodifiableList(warnings));
    }

    /**
     * Truncate a string for safe display in warnings.
     */
    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }
}
