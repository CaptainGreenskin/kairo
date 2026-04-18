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
import io.kairo.api.model.IntRange;
import io.kairo.api.model.ModelCapability;
import io.kairo.api.model.ToolVerbosity;
import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.core.tool.DefaultToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemPromptBuilderTest {

    // ---- helpers ----

    private static ToolDefinition tool(String name, String description, ToolCategory category) {
        return new ToolDefinition(
                name,
                description,
                category,
                new JsonSchema("object", null, null, null),
                Object.class);
    }

    private static ToolDefinition toolWithGuidance(
            String name, String description, ToolCategory category, String guidance) {
        return new ToolDefinition(
                name,
                description,
                category,
                new JsonSchema("object", null, null, null),
                Object.class,
                null,
                null,
                guidance);
    }

    private DefaultToolRegistry registryWith(ToolDefinition... tools) {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        for (ToolDefinition t : tools) {
            registry.register(t);
        }
        return registry;
    }

    private static ModelCapability conciseModel() {
        return new ModelCapability(
                "claude", "haiku", 200_000, 4096, false, true, ToolVerbosity.CONCISE, null, "");
    }

    private static ModelCapability standardModel() {
        return new ModelCapability(
                "claude",
                "sonnet",
                200_000,
                8192,
                true,
                true,
                ToolVerbosity.STANDARD,
                new IntRange(1024, 8192),
                "");
    }

    private static ModelCapability gptModel() {
        return new ModelCapability(
                "gpt", "4o", 128_000, 4096, false, false, ToolVerbosity.STANDARD, null);
    }

    private static SkillDefinition skill(String name, String description, String instructions) {
        return new SkillDefinition(
                name,
                "1.0",
                description,
                instructions,
                List.of("trigger:" + name),
                SkillCategory.GENERAL);
    }

    // ---- 1. Static / Dynamic Separation ----

    @Test
    void testStaticDynamicSeparation() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are a helpful assistant.")
                        .section("rules", "Always be concise.")
                        .dynamicBoundary()
                        .section("context", "Working directory: /home/user")
                        .section("status", "Git branch: main")
                        .buildResult();

        // Static prefix should contain identity and rules
        assertTrue(result.staticPrefix().contains("You are a helpful assistant."));
        assertTrue(result.staticPrefix().contains("Always be concise."));
        assertFalse(result.staticPrefix().contains("Working directory"));
        assertFalse(result.staticPrefix().contains("Git branch"));

        // Dynamic suffix should contain context and status
        assertTrue(result.dynamicSuffix().contains("Working directory: /home/user"));
        assertTrue(result.dynamicSuffix().contains("Git branch: main"));
        assertFalse(result.dynamicSuffix().contains("You are a helpful assistant."));

        // Full prompt should contain everything
        assertTrue(result.fullPrompt().contains("You are a helpful assistant."));
        assertTrue(result.fullPrompt().contains("Always be concise."));
        assertTrue(result.fullPrompt().contains("Working directory: /home/user"));
        assertTrue(result.fullPrompt().contains("Git branch: main"));

        assertTrue(result.hasBoundary());
    }

    // ---- 2. Dynamic Boundary Position ----

    @Test
    void testDynamicBoundaryPosition() {
        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .section("s1", "Static section one")
                        .section("s2", "Static section two")
                        .dynamicBoundary()
                        .section("d1", "Dynamic section one");

        List<SystemPromptBuilder.PromptSection> sections = builder.sections();

        assertFalse(sections.get(0).isDynamic(), "Section before boundary should be static");
        assertFalse(sections.get(1).isDynamic(), "Section before boundary should be static");
        assertTrue(sections.get(2).isDynamic(), "Section after boundary should be dynamic");
    }

    @Test
    void testDynamicBoundaryAtStart() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .dynamicBoundary()
                        .section("ctx", "Everything is dynamic")
                        .buildResult();

        assertTrue(result.staticPrefix().isEmpty());
        assertTrue(result.dynamicSuffix().contains("Everything is dynamic"));
    }

    // ---- 3. Tool Overview Injection ----

    @Test
    void testToolOverviewInjection() {
        DefaultToolRegistry registry =
                registryWith(
                        tool("read_file", "Read a file from disk", ToolCategory.FILE_AND_CODE),
                        tool("web_search", "Search the web", ToolCategory.INFORMATION));

        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .addToolOverview(registry)
                        .build();

        assertTrue(prompt.contains("Available Tools"), "Should contain tool section header");
        assertTrue(prompt.contains("read_file"), "Should contain tool name");
        assertTrue(prompt.contains("Read a file from disk"), "Should contain tool description");
        assertTrue(prompt.contains("web_search"));
        assertTrue(prompt.contains("Search the web"));
    }

    @Test
    void testToolOverviewWithUsageGuidance() {
        DefaultToolRegistry registry =
                registryWith(
                        toolWithGuidance(
                                "edit_file",
                                "Edit a file",
                                ToolCategory.FILE_AND_CODE,
                                "Always read the file before editing."));

        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .addToolOverview(registry)
                        .build();

        assertTrue(prompt.contains("edit_file"));
        assertTrue(prompt.contains("Edit a file"));
        assertTrue(prompt.contains("Usage guidance: Always read the file before editing."));
    }

    // ---- 4. Tool Overview with No Tools ----

    @Test
    void testToolOverviewWithNoTools() {
        DefaultToolRegistry emptyRegistry = new DefaultToolRegistry();

        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .addToolOverview(emptyRegistry)
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
        assertFalse(
                prompt.contains("Available Tools"),
                "Should not contain tool section when no tools are registered");
    }

    @Test
    void testToolOverviewWithNullRegistry() {
        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .addToolOverview(null)
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
    }

    // ---- 5. CacheScope Marking ----

    @Test
    void testCacheScopeMarking() {
        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.", CacheScope.GLOBAL)
                        .section("tools", "Tool listing here.", CacheScope.SESSION)
                        .section("context", "Runtime context.", CacheScope.NONE);

        List<SystemPromptSegment> segments = builder.buildSegments();

        assertEquals(3, segments.size());

        assertEquals(CacheScope.GLOBAL, segments.get(0).scope());
        assertEquals("identity", segments.get(0).name());
        assertTrue(segments.get(0).isCacheable());

        assertEquals(CacheScope.SESSION, segments.get(1).scope());
        assertEquals("tools", segments.get(1).name());
        assertTrue(segments.get(1).isCacheable());

        assertEquals(CacheScope.NONE, segments.get(2).scope());
        assertEquals("context", segments.get(2).name());
        assertFalse(segments.get(2).isCacheable());
    }

    @Test
    void testDefaultCacheScopeBeforeBoundary() {
        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .section("identity", "Static content")
                        .dynamicBoundary()
                        .section("context", "Dynamic content");

        List<SystemPromptBuilder.PromptSection> sections = builder.sections();

        assertEquals(
                CacheScope.GLOBAL,
                sections.get(0).cacheScope(),
                "Before boundary should default to GLOBAL");
        assertEquals(
                CacheScope.NONE,
                sections.get(1).cacheScope(),
                "After boundary should default to NONE");
    }

    // ---- 6. Custom System Prompt (Legacy API) ----

    @Test
    void testCustomSystemPrompt() {
        String prompt =
                SystemPromptBuilder.create().base("You are Kairo, an AI coding assistant.").build();

        assertEquals("You are Kairo, an AI coding assistant.", prompt);
    }

    @Test
    void testLegacyBaseWithContext() {
        String prompt =
                SystemPromptBuilder.create()
                        .base("You are Kairo.")
                        .addContext("Working directory: /project")
                        .build();

        assertTrue(prompt.startsWith("You are Kairo."));
        assertTrue(prompt.contains("# Context"));
        assertTrue(prompt.contains("Working directory: /project"));
    }

    @Test
    void testLegacyBaseWithToolsAndContext() {
        DefaultToolRegistry registry =
                registryWith(tool("run_cmd", "Run a shell command", ToolCategory.EXECUTION));

        String prompt =
                SystemPromptBuilder.create()
                        .base("You are an assistant.")
                        .addToolOverview(registry)
                        .addContext("Project: kairo")
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
        assertTrue(prompt.contains("run_cmd"));
        assertTrue(prompt.contains("Run a shell command"));
        assertTrue(prompt.contains("# Context"));
        assertTrue(prompt.contains("Project: kairo"));
    }

    // ---- 7. Prompt with Skills ----

    @Test
    void testPromptWithSkills() {
        List<SkillDefinition> skills =
                List.of(
                        skill("commit", "Generate git commits", "Use conventional commits format."),
                        skill(
                                "review",
                                "Review pull requests",
                                "Check for bugs and style issues."));

        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .addSkillOverview(skills)
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
        assertTrue(prompt.contains("commit"));
        assertTrue(prompt.contains("review"));
    }

    @Test
    void testSkillOverviewCacheScope() {
        List<SkillDefinition> skills =
                List.of(skill("commit", "Generate git commits", "Use conventional commits."));

        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .addSkillOverview(skills);

        List<SystemPromptSegment> segments = builder.buildSegments();

        SystemPromptSegment skillSegment =
                segments.stream().filter(s -> "skills".equals(s.name())).findFirst().orElse(null);
        assertNotNull(skillSegment, "Should have a skills segment");
        assertEquals(
                CacheScope.SESSION, skillSegment.scope(), "Skill section should be SESSION-scoped");
    }

    // ---- 8. Prompt with Memory/Context ----

    @Test
    void testPromptWithMemoryContext() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .dynamicBoundary()
                        .addContext("User prefers dark mode. Previous conversation about testing.")
                        .buildResult();

        assertTrue(result.dynamicSuffix().contains("User prefers dark mode"));
        assertTrue(
                result.fullPrompt()
                        .contains("User prefers dark mode. Previous conversation about testing."));
    }

    // ---- 9. Build with Minimal Config ----

    @Test
    void testBuildWithMinimalConfig() {
        String prompt =
                SystemPromptBuilder.create().section("identity", "You are an assistant.").build();

        assertTrue(prompt.contains("# Identity"));
        assertTrue(prompt.contains("You are an assistant."));
    }

    @Test
    void testBuildWithEmptyBuilder() {
        String prompt = SystemPromptBuilder.create().build();
        assertTrue(prompt.isEmpty());
    }

    @Test
    void testNullAndBlankSectionsIgnored() {
        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .section("empty", null)
                        .section("blank", "   ")
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
        assertFalse(prompt.contains("Empty"));
        assertFalse(prompt.contains("Blank"));
    }

    // ---- 10. Build with Full Config ----

    @Test
    void testBuildWithFullConfig() {
        DefaultToolRegistry registry =
                registryWith(
                        tool("read_file", "Read a file", ToolCategory.FILE_AND_CODE),
                        tool("run_cmd", "Run a command", ToolCategory.EXECUTION));

        List<SkillDefinition> skills =
                List.of(skill("commit", "Generate commits", "Use conventional commits."));

        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .forModel(standardModel())
                        .section("identity", "You are Kairo, an AI coding assistant.")
                        .section("rules", "Always read before edit.")
                        .addToolOverview(registry)
                        .addSkillOverview(skills)
                        .dynamicBoundary()
                        .addContext("Working directory: /project")
                        .featureGate("multiAgent", true, "You can delegate tasks to sub-agents.")
                        .buildResult();

        String full = result.fullPrompt();
        assertTrue(full.contains("You are Kairo"));
        assertTrue(full.contains("Always read before edit"));
        assertTrue(full.contains("read_file"));
        assertTrue(full.contains("run_cmd"));
        assertTrue(full.contains("commit"));
        assertTrue(full.contains("Working directory: /project"));
        assertTrue(full.contains("delegate tasks to sub-agents"));

        assertTrue(result.staticPrefix().contains("You are Kairo"));
        assertTrue(result.staticPrefix().contains("Always read before edit"));

        assertTrue(result.dynamicSuffix().contains("Working directory: /project"));
        assertTrue(result.dynamicSuffix().contains("delegate tasks to sub-agents"));

        assertTrue(result.hasSegments());
        assertFalse(result.segments().isEmpty());
    }

    // ---- Feature Gate ----

    @Test
    void testFeatureGateEnabled() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .dynamicBoundary()
                        .featureGate("multiAgent", true, "Multi-agent instructions here.")
                        .buildResult();

        assertTrue(result.fullPrompt().contains("Multi-agent instructions here."));
        assertTrue(result.dynamicSuffix().contains("Multi-agent instructions here."));
    }

    @Test
    void testFeatureGateDisabled() {
        SystemPromptResult result =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .dynamicBoundary()
                        .featureGate("multiAgent", false, "Multi-agent instructions here.")
                        .buildResult();

        assertFalse(result.fullPrompt().contains("Multi-agent instructions"));
    }

    @Test
    void testFeatureGateAlwaysDynamic() {
        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .featureGate("earlyFeature", true, "Early feature instructions.")
                        .section("identity", "You are an assistant.")
                        .dynamicBoundary();

        SystemPromptBuilder.PromptSection featureSection =
                builder.sections().stream()
                        .filter(s -> s.name().startsWith("feature:"))
                        .findFirst()
                        .orElse(null);
        assertNotNull(featureSection);
        assertTrue(featureSection.isDynamic(), "Feature gate sections should always be dynamic");
        assertEquals(CacheScope.SESSION, featureSection.cacheScope());
    }

    // ---- Model-aware behavior ----

    @Test
    void testConciseModelSkipsExamples() {
        String prompt =
                SystemPromptBuilder.create()
                        .forModel(conciseModel())
                        .section("identity", "You are an assistant.")
                        .section("examples", "Example 1: Hello world.")
                        .section("rules", "Be concise.")
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
        assertTrue(prompt.contains("Be concise."));
        assertFalse(prompt.contains("Example 1"), "CONCISE model should skip example sections");
    }

    @Test
    void testConciseModelSkipsWorkedExamples() {
        String prompt =
                SystemPromptBuilder.create()
                        .forModel(conciseModel())
                        .section("identity", "You are an assistant.")
                        .section("worked-examples", "Worked example: Step by step.")
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
        assertFalse(
                prompt.contains("Worked example"),
                "CONCISE model should skip worked-examples sections");
    }

    @Test
    void testStandardModelKeepsExamples() {
        String prompt =
                SystemPromptBuilder.create()
                        .forModel(standardModel())
                        .section("identity", "You are an assistant.")
                        .section("examples", "Example 1: Hello world.")
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
        assertTrue(prompt.contains("Example 1: Hello world."));
    }

    @Test
    void testModelPromptGuidanceInjected() {
        String prompt =
                SystemPromptBuilder.create()
                        .forModel(gptModel())
                        .section("identity", "You are an assistant.")
                        .build();

        assertTrue(prompt.contains("You are an assistant."));
        assertTrue(
                prompt.contains("Always use tools to take action"),
                "GPT model should get default prompt guidance injected");
    }

    @Test
    void testModelGuidanceNotDuplicated() {
        SystemPromptBuilder builder =
                SystemPromptBuilder.create()
                        .forModel(gptModel())
                        .section("identity", "You are an assistant.");

        String prompt = builder.build();
        assertTrue(prompt.contains("Always use tools to take action"));

        int count = countOccurrences(prompt, "Always use tools to take action");
        assertEquals(1, count, "Model guidance should appear exactly once");
    }

    // ---- Section heading formatting ----

    @Test
    void testSectionHeadingsCapitalized() {
        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .section("rules", "Be helpful.")
                        .build();

        assertTrue(prompt.contains("# Identity"));
        assertTrue(prompt.contains("# Rules"));
    }

    // ---- Segments API ----

    @Test
    void testBuildSegmentsContent() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.", CacheScope.GLOBAL)
                        .section("tools", "Tool list.", CacheScope.SESSION)
                        .buildSegments();

        assertEquals(2, segments.size());
        assertTrue(segments.get(0).content().startsWith("# Identity"));
        assertTrue(segments.get(0).content().contains("You are an assistant."));
        assertTrue(segments.get(1).content().startsWith("# Tools"));
        assertTrue(segments.get(1).content().contains("Tool list."));
    }

    @Test
    void testBuildSegmentsFromLegacyAPI() {
        List<SystemPromptSegment> segments =
                SystemPromptBuilder.create()
                        .base("You are an assistant.")
                        .addContext("Project: kairo")
                        .buildSegments();

        assertFalse(segments.isEmpty());
        List<String> names = segments.stream().map(SystemPromptSegment::name).toList();
        assertTrue(names.contains("system"), "Should have system segment from base()");
        assertTrue(names.contains("context"), "Should have context segment from addContext()");
    }

    // ---- Tool grouping by category ----

    @Test
    void testToolsGroupedByCategory() {
        DefaultToolRegistry registry =
                registryWith(
                        tool("read_file", "Read a file", ToolCategory.FILE_AND_CODE),
                        tool("write_file", "Write a file", ToolCategory.FILE_AND_CODE),
                        tool("web_search", "Search the web", ToolCategory.INFORMATION));

        String prompt =
                SystemPromptBuilder.create()
                        .section("identity", "You are an assistant.")
                        .addToolOverview(registry)
                        .build();

        assertTrue(prompt.contains("FILE_AND_CODE"));
        assertTrue(prompt.contains("INFORMATION"));
    }

    // ---- Utility ----

    private static int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
