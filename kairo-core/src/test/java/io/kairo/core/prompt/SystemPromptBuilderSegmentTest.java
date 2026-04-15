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
package io.kairo.core.prompt;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.CacheScope;
import io.kairo.api.context.SystemPromptSegment;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemPromptBuilderSegmentTest {

    @Test
    void buildSegments_sectionsBeforeBoundary_defaultToGlobal() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coding assistant.")
                        .section("rules", "Follow clean code.")
                        .dynamicBoundary()
                        .buildSegments();

        assertEquals(2, segments.size());
        assertEquals(CacheScope.GLOBAL, segments.get(0).scope());
        assertEquals(CacheScope.GLOBAL, segments.get(1).scope());
    }

    @Test
    void buildSegments_sectionsAfterBoundary_defaultToNone() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coding assistant.")
                        .dynamicBoundary()
                        .section("context", "Working dir: /tmp")
                        .buildSegments();

        assertEquals(2, segments.size());
        assertEquals(CacheScope.GLOBAL, segments.get(0).scope());
        assertEquals(CacheScope.NONE, segments.get(1).scope());
    }

    @Test
    void buildSegments_explicitScope_overridesDefault() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("tools", "tool list here", CacheScope.SESSION)
                        .buildSegments();

        assertEquals(1, segments.size());
        assertEquals(CacheScope.SESSION, segments.get(0).scope());
        assertEquals("tools", segments.get(0).name());
    }

    @Test
    void buildSegments_featureGate_defaultsToSession() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coding assistant.")
                        .featureGate("multiAgent", true, "You can spawn sub-agents.")
                        .buildSegments();

        assertEquals(2, segments.size());
        assertEquals(CacheScope.SESSION, segments.get(1).scope());
        assertEquals("feature:multiAgent", segments.get(1).name());
    }

    @Test
    void buildSegments_featureGate_disabledIsSkipped() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coding assistant.")
                        .featureGate("multiAgent", false, "You can spawn sub-agents.")
                        .buildSegments();

        assertEquals(1, segments.size());
    }

    @Test
    void buildSegments_mixedScopes_correctOrder() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are helpful.", CacheScope.GLOBAL)
                        .section("tools", "Available tools: read", CacheScope.SESSION)
                        .section("context", "Date: today", CacheScope.NONE)
                        .buildSegments();

        assertEquals(3, segments.size());
        assertEquals(CacheScope.GLOBAL, segments.get(0).scope());
        assertEquals("identity", segments.get(0).name());
        assertEquals(CacheScope.SESSION, segments.get(1).scope());
        assertEquals("tools", segments.get(1).name());
        assertEquals(CacheScope.NONE, segments.get(2).scope());
        assertEquals("context", segments.get(2).name());
    }

    @Test
    void buildResult_containsSegments() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are helpful.")
                        .dynamicBoundary()
                        .section("context", "Working dir: /tmp")
                        .buildResult();

        assertTrue(result.hasSegments());
        assertFalse(result.segments().isEmpty());
    }

    @Test
    void buildResult_backwardCompat_noSegmentsWhenLegacy() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .base("You are a helpful assistant.")
                        .addContext("Working dir: /tmp")
                        .buildResult();

        assertFalse(result.fullPrompt().isEmpty());
        // Legacy path converts to sections, so segments should be present
        assertTrue(result.hasSegments());
    }

    @Test
    void buildSegments_emptyContent_skipped() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are helpful.")
                        .section("empty", "", CacheScope.GLOBAL)
                        .section("blank", "   ", CacheScope.GLOBAL)
                        .section("nullContent", null, CacheScope.GLOBAL)
                        .buildSegments();

        assertEquals(1, segments.size());
        assertEquals("identity", segments.get(0).name());
    }

    @Test
    void buildSegments_conciseModel_skipsExamples() {
        ModelCapability concise =
                new ModelCapability(
                        "claude", "haiku", 200000, 4096, false, true, ToolVerbosity.CONCISE, null);

        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .forModel(concise)
                        .section("identity", "You are helpful.")
                        .section("examples", "Example 1: do this.", CacheScope.GLOBAL)
                        .section("rules", "Follow clean code.")
                        .buildSegments();

        assertEquals(2, segments.size());
        assertEquals("identity", segments.get(0).name());
        assertEquals("rules", segments.get(1).name());
    }

    @Test
    void buildSegments_standardModel_keepsExamples() {
        ModelCapability standard =
                new ModelCapability(
                        "claude", "sonnet", 200000, 4096, true, true, ToolVerbosity.STANDARD, null);

        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .forModel(standard)
                        .section("identity", "You are helpful.")
                        .section("examples", "Example 1: do this.", CacheScope.GLOBAL)
                        .buildSegments();

        assertEquals(2, segments.size());
    }

    // ---- Integration: Builder -> ModelConfig round-trip ----

    @Test
    void fullRoundTrip_builderToModelConfig() {
        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coding assistant.")
                        .section("rules", "Follow clean code practices.")
                        .dynamicBoundary()
                        .section("context", "Working dir: /tmp")
                        .featureGate("multiAgent", true, "You can spawn sub-agents.");

        List<SystemPromptSegment> segments = builder.buildSegments();

        assertEquals(4, segments.size());
        assertEquals(CacheScope.GLOBAL, segments.get(0).scope()); // identity
        assertEquals(CacheScope.GLOBAL, segments.get(1).scope()); // rules
        assertEquals(CacheScope.NONE, segments.get(2).scope()); // context
        assertEquals(CacheScope.SESSION, segments.get(3).scope()); // featureGate

        SystemPromptResult result = builder.buildResult();
        assertTrue(result.hasSegments());
        assertEquals(4, result.segments().size());
        assertTrue(result.hasBoundary());
    }

    @Test
    void buildSegments_contentIsFormatted() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are helpful.")
                        .buildSegments();

        // buildSegments() formats with "# Name\n\ncontent"
        assertEquals("# Identity\n\nYou are helpful.", segments.get(0).content());
    }

    // ---- addSkillOverview tests ----

    @Test
    void addSkillOverviewWithBudget() {
        List<SkillDefinition> skills = List.of(
                new SkillDefinition(
                        "commit", "1.0.0", "Git commit helper", null, List.of(),
                        SkillCategory.CODE),
                new SkillDefinition(
                        "deploy", "2.0.0", "Deploy assistant", null, List.of(),
                        SkillCategory.DEVOPS));

        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are helpful.")
                        .addSkillOverview(skills, 10000)
                        .buildSegments();

        // Should have identity + skills sections
        assertEquals(2, segments.size());
        assertEquals("skills", segments.get(1).name());
        assertEquals(CacheScope.SESSION, segments.get(1).scope());
        assertTrue(segments.get(1).content().contains("commit"));
        assertTrue(segments.get(1).content().contains("deploy"));
    }

    @Test
    void addSkillOverviewEmptyList() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are helpful.")
                        .addSkillOverview(List.of(), 5000)
                        .buildSegments();

        // Empty skills should not add a section
        assertEquals(1, segments.size());
        assertEquals("identity", segments.get(0).name());
    }

    @Test
    void addSkillOverviewDefaultBudget() {
        List<SkillDefinition> skills = List.of(
                new SkillDefinition(
                        "lint", "1.0.0", "Linter tool", null, List.of(),
                        SkillCategory.CODE));

        // Use the overload without explicit budget
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are helpful.")
                        .addSkillOverview(skills)
                        .buildSegments();

        assertEquals(2, segments.size());
        assertEquals("skills", segments.get(1).name());
        assertEquals(CacheScope.SESSION, segments.get(1).scope());
        assertTrue(segments.get(1).content().contains("lint"));
    }
}
