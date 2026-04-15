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
package io.kairo.core.skill;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultTriggerGuardTest {

    private DefaultTriggerGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DefaultTriggerGuard();
    }

    private SkillDefinition skill(String name, List<String> triggers) {
        return new SkillDefinition(
                name, "1.0.0", "desc", "instructions", triggers, SkillCategory.CODE);
    }

    // ---- Slash command matching ----

    @Test
    @DisplayName("Slash command /review matches skill 'review'")
    void testSlashCommandMatch() {
        assertTrue(guard.shouldActivate("/review", skill("review", List.of())));
    }

    @Test
    @DisplayName("Slash command /review with args matches")
    void testSlashCommandWithArgs() {
        assertTrue(guard.shouldActivate("/review this code", skill("review", List.of())));
    }

    @Test
    @DisplayName("Slash command /reviewcode should NOT match 'review' (no boundary)")
    void testSlashCommandNoBoundary() {
        assertFalse(guard.shouldActivate("/reviewcode", skill("review", List.of())));
    }

    @Test
    @DisplayName("Slash command is case-insensitive")
    void testSlashCommandCaseInsensitive() {
        assertTrue(guard.shouldActivate("/REVIEW", skill("review", List.of())));
    }

    // ---- Exact name matching with word boundaries ----

    @Test
    @DisplayName("Exact name 'review' matches in input")
    void testExactNameMatch() {
        assertTrue(guard.shouldActivate("please review my code", skill("review", List.of())));
    }

    @Test
    @DisplayName("Name 'review' should NOT match 'preview'")
    void testWordBoundaryPreventsPartialMatch() {
        assertFalse(guard.shouldActivate("preview my document", skill("review", List.of())));
    }

    @Test
    @DisplayName("Name match is case-insensitive")
    void testNameMatchCaseInsensitive() {
        assertTrue(guard.shouldActivate("Please REVIEW this", skill("review", List.of())));
    }

    // ---- Trigger condition matching ----

    @Test
    @DisplayName("All keywords in trigger must appear in input")
    void testAllKeywordsMustMatch() {
        SkillDefinition skill = skill("code-review", List.of("review code"));

        assertTrue(guard.shouldActivate("please review my code", skill));
        assertFalse(guard.shouldActivate("please review my document", skill));
    }

    @Test
    @DisplayName("Slash trigger in triggerConditions list")
    void testSlashTriggerInConditions() {
        SkillDefinition skill = skill("code-review", List.of("/cr"));

        assertTrue(guard.shouldActivate("/cr", skill));
        assertTrue(guard.shouldActivate("/cr some args", skill));
        assertFalse(guard.shouldActivate("/create", skill));
    }

    // ---- Stop-word filtering ----

    @Test
    @DisplayName("Stop words are filtered from keyword matching")
    void testStopWordFiltering() {
        // Trigger "review the code" — "the" is a stop word, so only "review" and "code" matter
        SkillDefinition skill = skill("code-review", List.of("review the code"));

        assertTrue(guard.shouldActivate("review code", skill));
    }

    // ---- Anti-pollution: vague inputs should NOT trigger ----

    @Test
    @DisplayName("Vague input should not trigger skill")
    void testVagueInputDoesNotTrigger() {
        SkillDefinition skill = skill("code-review", List.of("review code"));

        assertFalse(guard.shouldActivate("help me with something", skill));
        assertFalse(guard.shouldActivate("how do I do this?", skill));
    }

    @Test
    @DisplayName("Empty input should not trigger")
    void testEmptyInput() {
        assertFalse(guard.shouldActivate("", skill("review", List.of())));
        assertFalse(guard.shouldActivate("  ", skill("review", List.of())));
    }

    @Test
    @DisplayName("Null input should not trigger")
    void testNullInput() {
        assertFalse(guard.shouldActivate(null, skill("review", List.of())));
    }

    @Test
    @DisplayName("Null skill should not trigger")
    void testNullSkill() {
        assertFalse(guard.shouldActivate("/review", null));
    }

    @Test
    @DisplayName("Null triggerConditions list is handled gracefully")
    void testNullTriggerConditions() {
        SkillDefinition skill =
                new SkillDefinition("xyz", "1.0.0", "desc", "body", null, SkillCategory.CODE);

        // Exact name "xyz" should still match
        assertTrue(guard.shouldActivate("use xyz now", skill));
        // Unrelated input doesn't match
        assertFalse(guard.shouldActivate("something else", skill));
    }

    // ---- Threshold ----

    @Test
    @DisplayName("Default threshold is 0.8")
    void testDefaultThreshold() {
        assertEquals(0.8f, guard.confidenceThreshold(), 0.001f);
    }

    @Test
    @DisplayName("Custom threshold can be set")
    void testCustomThreshold() {
        DefaultTriggerGuard custom = new DefaultTriggerGuard(0.5f);
        assertEquals(0.5f, custom.confidenceThreshold(), 0.001f);
    }

    @Test
    @DisplayName("Invalid threshold throws IllegalArgumentException")
    void testInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new DefaultTriggerGuard(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> new DefaultTriggerGuard(1.1f));
    }

    // ---- Edge cases ----

    @Test
    @DisplayName("Single-char keywords are filtered out")
    void testSingleCharKeywordsFiltered() {
        // Trigger "a b c" — all single chars get filtered, so triggerKeywords is empty → no match
        SkillDefinition skill = skill("test", List.of("a b c"));

        assertFalse(guard.shouldActivate("a b c", skill));
    }

    @Test
    @DisplayName("Trigger with only stop words returns false")
    void testTriggerOnlyStopWords() {
        SkillDefinition skill = skill("test", List.of("the is a"));

        assertFalse(guard.shouldActivate("the is a good thing", skill));
    }

    @Test
    @DisplayName("Multiple trigger conditions — first match wins")
    void testMultipleTriggers() {
        SkillDefinition skill = skill("deploy", List.of("deploy application", "/ship"));

        assertTrue(guard.shouldActivate("deploy application now", skill));
        assertTrue(guard.shouldActivate("/ship", skill));
        assertFalse(guard.shouldActivate("build application", skill));
    }
}
