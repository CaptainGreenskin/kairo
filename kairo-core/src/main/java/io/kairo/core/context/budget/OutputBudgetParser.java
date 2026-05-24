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
package io.kairo.core.context.budget;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@link OutputBudget} declarations from user input.
 *
 * <p>Mirrors Claude Code's three-pattern parser so users can paste the same prompts:
 *
 * <ul>
 *   <li><b>Shorthand at start</b> — {@code +500k} or {@code +2m} as the prompt's first token
 *   <li><b>Shorthand at end</b> — {@code refactor this module +2m} (whitespace before the {@code
 *       +}, optional trailing punctuation)
 *   <li><b>Verbose</b> — {@code spend 2M tokens} / {@code use 1B tokens} anywhere in the prompt
 * </ul>
 *
 * <p>Unit suffixes are case-insensitive: {@code k} = thousand, {@code m} = million, {@code b} =
 * billion. Decimal multipliers (e.g. {@code +1.5m}) are honored.
 *
 * <p>The parser is a pure function — call {@link #parse(String)} for the budget alone, or {@link
 * #parseAndStrip(String)} when you also want the original prompt with the budget syntax removed (so
 * the model isn't distracted by "+500k" appearing twice — once in the prompt, once surfaced via
 * system attachment).
 */
public final class OutputBudgetParser {

    private static final Pattern SHORTHAND_START =
            Pattern.compile(
                    "^\\s*\\+(\\d+(?:\\.\\d+)?)\\s*([kmbKMB])\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern SHORTHAND_END =
            Pattern.compile(
                    "(?<=\\s)\\+(\\d+(?:\\.\\d+)?)\\s*([kmbKMB])\\s*[.!?]?\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern VERBOSE =
            Pattern.compile(
                    "\\b(?:use|spend)\\s+(\\d+(?:\\.\\d+)?)\\s*([kmbKMB])\\s*tokens?\\b",
                    Pattern.CASE_INSENSITIVE);

    private OutputBudgetParser() {}

    /** Parse a single {@link OutputBudget} from {@code text}, if any pattern matches. */
    public static Optional<OutputBudget> parse(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        for (Pattern p : new Pattern[] {SHORTHAND_START, SHORTHAND_END, VERBOSE}) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                return Optional.of(buildBudget(m.group(1), m.group(2)));
            }
        }
        return Optional.empty();
    }

    /**
     * Parse the budget AND return the original text with the budget syntax removed. Useful when the
     * budget is surfaced to the model via a separate system attachment — leaving the literal
     * "+500k" in the prompt would otherwise read like a typo to the model.
     */
    public static ParseResult parseAndStrip(String text) {
        if (text == null || text.isBlank()) {
            return new ParseResult(Optional.empty(), text);
        }
        // Priority: SHORTHAND_END first (the most position-specific), then START, then VERBOSE.
        // Otherwise "+2m" appearing at both ends of a long prompt would only strip the START
        // match and leave the END one behind, confusing the model.
        for (Pattern p : new Pattern[] {SHORTHAND_END, SHORTHAND_START, VERBOSE}) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                OutputBudget budget = buildBudget(m.group(1), m.group(2));
                String stripped = m.replaceFirst("").trim();
                // Collapse interior double-spaces left by the strip so the model sees clean text.
                stripped = stripped.replaceAll("\\s{2,}", " ");
                return new ParseResult(Optional.of(budget), stripped);
            }
        }
        return new ParseResult(Optional.empty(), text);
    }

    private static OutputBudget buildBudget(String numberStr, String unitStr) {
        double n = Double.parseDouble(numberStr);
        long multiplier =
                switch (unitStr.toLowerCase()) {
                    case "k" -> 1_000L;
                    case "m" -> 1_000_000L;
                    case "b" -> 1_000_000_000L;
                    default -> throw new IllegalArgumentException("Unknown unit: " + unitStr);
                };
        long tokens = Math.round(n * multiplier);
        return new OutputBudget(tokens);
    }

    /**
     * Result of {@link #parseAndStrip(String)}: the parsed budget (if any) and the prompt with the
     * budget syntax removed.
     */
    public record ParseResult(Optional<OutputBudget> budget, String strippedPrompt) {}
}
