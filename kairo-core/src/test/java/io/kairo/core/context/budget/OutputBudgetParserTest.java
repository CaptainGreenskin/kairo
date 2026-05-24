/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.context.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutputBudgetParserTest {

    @Test
    void parse_shorthandAtStart() {
        assertThat(OutputBudgetParser.parse("+500k explain this codebase"))
                .map(OutputBudget::totalTokens)
                .contains(500_000L);
    }

    @Test
    void parse_shorthandAtEnd_withPunctuation() {
        assertThat(OutputBudgetParser.parse("refactor the auth module +2m."))
                .map(OutputBudget::totalTokens)
                .contains(2_000_000L);
    }

    @Test
    void parse_verbose_lowercase() {
        assertThat(OutputBudgetParser.parse("please spend 750k tokens on this"))
                .map(OutputBudget::totalTokens)
                .contains(750_000L);
    }

    @Test
    void parse_verbose_useSynonym() {
        assertThat(OutputBudgetParser.parse("use 1B tokens for the migration"))
                .map(OutputBudget::totalTokens)
                .contains(1_000_000_000L);
    }

    @Test
    void parse_decimalMultiplier() {
        // +1.5m → 1,500,000
        assertThat(OutputBudgetParser.parse("+1.5m do everything"))
                .map(OutputBudget::totalTokens)
                .contains(1_500_000L);
    }

    @Test
    void parse_caseInsensitiveUnit() {
        assertThat(OutputBudgetParser.parse("+500K"))
                .map(OutputBudget::totalTokens)
                .contains(500_000L);
        assertThat(OutputBudgetParser.parse("+2M"))
                .map(OutputBudget::totalTokens)
                .contains(2_000_000L);
    }

    @Test
    void parse_noMatch_returnsEmpty() {
        assertThat(OutputBudgetParser.parse("hello world")).isEmpty();
        assertThat(OutputBudgetParser.parse("")).isEmpty();
        assertThat(OutputBudgetParser.parse(null)).isEmpty();
        // No unit suffix is not a match.
        assertThat(OutputBudgetParser.parse("+500 tokens")).isEmpty();
        // The shorthand needs to be position-bound; an in-place "+5k" is not picked up.
        assertThat(OutputBudgetParser.parse("interest rate is 5k+ today")).isEmpty();
    }

    @Test
    void parse_endShorthand_requiresLeadingWhitespace() {
        // No space before `+` → not a budget; protects against false positives like "1+2m=...".
        assertThat(OutputBudgetParser.parse("score 1+2m")).isEmpty();
    }

    @Test
    void parseAndStrip_startShorthand_removesAndTrims() {
        OutputBudgetParser.ParseResult r =
                OutputBudgetParser.parseAndStrip("+500k explain the codebase");
        assertThat(r.budget()).map(OutputBudget::totalTokens).contains(500_000L);
        assertThat(r.strippedPrompt()).isEqualTo("explain the codebase");
    }

    @Test
    void parseAndStrip_endShorthand_removesAndTrims() {
        OutputBudgetParser.ParseResult r =
                OutputBudgetParser.parseAndStrip("refactor the auth module +2m.");
        assertThat(r.budget()).map(OutputBudget::totalTokens).contains(2_000_000L);
        assertThat(r.strippedPrompt()).isEqualTo("refactor the auth module");
    }

    @Test
    void parseAndStrip_verbose_removesAndCollapsesSpaces() {
        OutputBudgetParser.ParseResult r =
                OutputBudgetParser.parseAndStrip("please spend 1m tokens and report back");
        assertThat(r.budget()).map(OutputBudget::totalTokens).contains(1_000_000L);
        // Double-space left by removal collapsed back to single space.
        assertThat(r.strippedPrompt()).isEqualTo("please and report back");
    }

    @Test
    void parseAndStrip_noMatch_returnsOriginal() {
        OutputBudgetParser.ParseResult r =
                OutputBudgetParser.parseAndStrip("just a regular prompt");
        assertThat(r.budget()).isEmpty();
        assertThat(r.strippedPrompt()).isEqualTo("just a regular prompt");
    }

    @Test
    void parseAndStrip_endPattern_requiresEndOfString() {
        // The end pattern is anchored at $, so "+500k more" doesn't match (text after the +500k).
        // The START pattern wins instead → leftover prompt is the rest of the line.
        OutputBudgetParser.ParseResult r = OutputBudgetParser.parseAndStrip("+500k do +500k more");
        assertThat(r.budget()).map(OutputBudget::totalTokens).contains(500_000L);
        assertThat(r.strippedPrompt()).isEqualTo("do +500k more");
    }

    @Test
    void parseAndStrip_endPattern_winsWhenAnchored() {
        // When the +Nm IS at end-of-string, END takes precedence over START.
        OutputBudgetParser.ParseResult r =
                OutputBudgetParser.parseAndStrip("+500k initial then more work +2m");
        assertThat(r.budget()).map(OutputBudget::totalTokens).contains(2_000_000L);
        assertThat(r.strippedPrompt()).isEqualTo("+500k initial then more work");
    }

    @Test
    void factory_helpers() {
        assertThat(OutputBudget.ofKilo(500).totalTokens()).isEqualTo(500_000L);
        assertThat(OutputBudget.ofMega(2).totalTokens()).isEqualTo(2_000_000L);
        assertThat(OutputBudget.ofTokens(12345).totalTokens()).isEqualTo(12345L);
    }

    @Test
    void outputBudget_rejectsNonPositive() {
        // The record enforces > 0 so downstream code can assume targetTokens is safe to divide by.
        assertThatThrowsForZero();
    }

    private static void assertThatThrowsForZero() {
        try {
            new OutputBudget(0);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // pass
        }
        try {
            new OutputBudget(-1);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    @Test
    void parse_billionScale() {
        Optional<OutputBudget> b = OutputBudgetParser.parse("+1b");
        assertThat(b).map(OutputBudget::totalTokens).contains(1_000_000_000L);
    }
}
