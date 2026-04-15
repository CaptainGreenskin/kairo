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
import static org.mockito.Mockito.*;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SystemPromptBuilderV2Test {

    // ---- Section-based building ----

    @Test
    void sectionBasedBuilding_multipleSections_allPresent() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coding assistant.")
                        .section("rules", "Always write tests.")
                        .section("style", "Use concise language.")
                        .buildResult();

        String full = result.fullPrompt();
        assertTrue(full.contains("You are a coding assistant."));
        assertTrue(full.contains("Always write tests."));
        assertTrue(full.contains("Use concise language."));
        // Section headings are capitalized
        assertTrue(full.contains("# Identity"));
        assertTrue(full.contains("# Rules"));
        assertTrue(full.contains("# Style"));
    }

    // ---- Dynamic boundary ----

    @Test
    void dynamicBoundary_splitsSectionsCorrectly() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are a helper.")
                        .section("rules", "Be safe.")
                        .dynamicBoundary()
                        .section("context", "Working dir: /tmp")
                        .buildResult();

        // Sections before boundary go to staticPrefix
        assertTrue(result.staticPrefix().contains("You are a helper."));
        assertTrue(result.staticPrefix().contains("Be safe."));
        assertFalse(result.staticPrefix().contains("Working dir:"));

        // Sections after boundary go to dynamicSuffix
        assertTrue(result.dynamicSuffix().contains("Working dir: /tmp"));
        assertFalse(result.dynamicSuffix().contains("You are a helper."));

        assertTrue(result.hasBoundary());
    }

    // ---- buildResult() returns correct SystemPromptResult ----

    @Test
    void buildResult_returnsCorrectParts() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("static1", "Static content one")
                        .dynamicBoundary()
                        .section("dynamic1", "Dynamic content one")
                        .buildResult();

        assertNotNull(result.staticPrefix());
        assertNotNull(result.dynamicSuffix());
        assertNotNull(result.fullPrompt());
        assertTrue(result.staticPrefix().contains("Static content one"));
        assertTrue(result.dynamicSuffix().contains("Dynamic content one"));
        assertTrue(result.fullPrompt().contains("Static content one"));
        assertTrue(result.fullPrompt().contains("Dynamic content one"));
    }

    // ---- build() backward compat ----

    @Test
    void build_returnsFullPromptString() {
        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are a bot.")
                        .dynamicBoundary()
                        .section("ctx", "Some context")
                        .build();

        assertTrue(prompt.contains("You are a bot."));
        assertTrue(prompt.contains("Some context"));
    }

    // ---- Feature gate: enabled ----

    @Test
    void featureGate_enabled_sectionIncluded() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "Base prompt")
                        .featureGate("multiAgent", true, "Team collaboration instructions")
                        .buildResult();

        assertTrue(result.fullPrompt().contains("Team collaboration instructions"));
    }

    // ---- Feature gate: disabled ----

    @Test
    void featureGate_disabled_sectionExcluded() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "Base prompt")
                        .featureGate("multiAgent", false, "Team collaboration instructions")
                        .buildResult();

        assertFalse(result.fullPrompt().contains("Team collaboration instructions"));
    }

    // ---- Feature gates are always dynamic ----

    @Test
    void featureGate_alwaysDynamic_evenBeforeBoundary() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "Base prompt")
                        .featureGate("experimental", true, "Experimental feature")
                        .dynamicBoundary()
                        .section("ctx", "Context info")
                        .buildResult();

        // Feature gate is marked as dynamic, so it should be in dynamicSuffix
        assertTrue(result.dynamicSuffix().contains("Experimental feature"));
        assertFalse(result.staticPrefix().contains("Experimental feature"));
    }

    // ---- Legacy addToolOverview() ----

    @Test
    void addToolOverview_legacyApi_works() {
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolDefinition tool =
                new ToolDefinition(
                        "bash",
                        "Run shell commands",
                        ToolCategory.EXECUTION,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class);
        when(registry.getAll()).thenReturn(List.of(tool));

        String prompt =
                SystemPromptBuilder.create()
                        .base("You are a helpful assistant.")
                        .addToolOverview(registry)
                        .build();

        assertTrue(prompt.contains("bash"));
        assertTrue(prompt.contains("Run shell commands"));
        assertTrue(prompt.contains("You are a helpful assistant."));
    }

    // ---- Legacy addContext() ----

    @Test
    void addContext_legacyApi_works() {
        String prompt =
                SystemPromptBuilder.create()
                        .base("You are a helper.")
                        .addContext("Working directory: /home/user/project")
                        .build();

        assertTrue(prompt.contains("Working directory: /home/user/project"));
        assertTrue(prompt.contains("You are a helper."));
    }

    // ---- Empty sections are skipped ----

    @Test
    void emptySections_skipped() {
        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .section("identity", "Valid content")
                        .section("empty", "")
                        .section("blank", "   ")
                        .section("nullContent", null);

        // Only the valid section should exist
        assertEquals(1, builder.sections().size());
        assertEquals("identity", builder.sections().get(0).name());
    }

    // ---- fullPrompt ≈ staticPrefix + dynamicSuffix ----

    @Test
    void fullPrompt_approximatelyEqualsStaticPlusDynamic() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are a coder.")
                        .section("rules", "Follow best practices.")
                        .dynamicBoundary()
                        .section("ctx", "Working in /tmp")
                        .section("session", "Previous session info")
                        .buildResult();

        // fullPrompt should contain all text from both static and dynamic parts
        String combined = result.staticPrefix() + "\n\n" + result.dynamicSuffix();
        // Both should contain the same information
        assertTrue(result.fullPrompt().contains("You are a coder."));
        assertTrue(result.fullPrompt().contains("Follow best practices."));
        assertTrue(result.fullPrompt().contains("Working in /tmp"));
        assertTrue(result.fullPrompt().contains("Previous session info"));
        assertTrue(combined.contains("You are a coder."));
        assertTrue(combined.contains("Working in /tmp"));
    }

    // ---- addToolOverview in section-based API ----

    @Test
    void addToolOverview_inSectionMode_addsSectionCorrectly() {
        ToolRegistry registry = mock(ToolRegistry.class);
        ToolDefinition tool =
                new ToolDefinition(
                        "read_file",
                        "Read a file",
                        ToolCategory.FILE_AND_CODE,
                        new JsonSchema("object", Map.of(), List.of(), null),
                        Object.class);
        when(registry.getAll()).thenReturn(List.of(tool));

        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "Helper bot")
                        .addToolOverview(registry)
                        .dynamicBoundary()
                        .section("ctx", "Context info")
                        .buildResult();

        assertTrue(result.fullPrompt().contains("read_file"));
        assertTrue(result.fullPrompt().contains("Read a file"));
    }

    // ---- addContext in section-based API ----

    @Test
    void addContext_inSectionMode_addsSectionCorrectly() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "Helper bot")
                        .dynamicBoundary()
                        .addContext("CWD: /home/user")
                        .buildResult();

        assertTrue(result.dynamicSuffix().contains("CWD: /home/user"));
    }

    // ---- No boundary set ----

    @Test
    void noBoundary_allSectionsInStaticPrefix() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "Helper")
                        .section("rules", "Be safe")
                        .buildResult();

        // Without boundary, hasBoundary() returns false (dynamicSuffix is empty)
        assertFalse(result.hasBoundary());
        assertTrue(result.staticPrefix().contains("Helper"));
        assertTrue(result.staticPrefix().contains("Be safe"));
    }

    // ---- addToolOverview with null registry ----

    @Test
    void addToolOverview_nullRegistry_noOp() {
        SystemPromptBuilder builder =
                SystemPromptBuilder.create().section("identity", "Helper").addToolOverview(null);

        assertEquals(1, builder.sections().size());
    }
}
