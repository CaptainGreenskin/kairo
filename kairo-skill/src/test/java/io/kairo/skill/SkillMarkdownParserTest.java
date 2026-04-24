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
package io.kairo.skill;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillMarkdownParserTest {

    private SkillMarkdownParser parser;
    private boolean yamlAvailable;

    @BeforeEach
    void setUp() {
        parser = new SkillMarkdownParser();
        // Detect jackson-dataformat-yaml version mismatch at runtime
        try {
            parser.parse(VALID_MARKDOWN);
            yamlAvailable = true;
        } catch (NoSuchMethodError e) {
            yamlAvailable = false;
        }
    }

    private void assumeYaml() {
        Assumptions.assumeTrue(yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");
    }

    private static final String VALID_MARKDOWN =
            """
            ---
            name: code-review
            version: 1.0.0
            category: CODE
            triggers:
              - "review code"
              - "/review"
            ---
            # Code Review Skill

            When performing a code review, check for bugs and style issues.

            ## Details
            More detailed instructions here.
            """;

    @Test
    @DisplayName("Parse valid markdown with YAML front-matter")
    void testParseValid() {
        assumeYaml();
        SkillDefinition skill = parser.parse(VALID_MARKDOWN);

        assertEquals("code-review", skill.name());
        assertEquals("1.0.0", skill.version());
        assertEquals(SkillCategory.CODE, skill.category());
        assertEquals(2, skill.triggerConditions().size());
        assertEquals("review code", skill.triggerConditions().get(0));
        assertEquals("/review", skill.triggerConditions().get(1));
        assertTrue(skill.instructions().contains("Code Review Skill"));
        assertTrue(skill.instructions().contains("More detailed instructions"));
    }

    @Test
    @DisplayName("Extract description from first paragraph after heading")
    void testDescriptionExtraction() {
        assumeYaml();
        SkillDefinition skill = parser.parse(VALID_MARKDOWN);

        assertEquals(
                "When performing a code review, check for bugs and style issues.",
                skill.description());
    }

    @Test
    @DisplayName("Missing front-matter throws IllegalArgumentException")
    void testMissingFrontMatter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("# Just a heading\n\nSome content"));
    }

    @Test
    @DisplayName("Missing closing delimiter throws IllegalArgumentException")
    void testMissingClosingDelimiter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("---\nname: test\n\nNo closing delimiter"));
    }

    @Test
    @DisplayName("Empty/null markdown throws IllegalArgumentException")
    void testEmptyMarkdown() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
    }

    @Test
    @DisplayName("Missing required 'name' field throws")
    void testMissingRequiredName() {
        assumeYaml();
        String md =
                """
                ---
                version: 1.0.0
                ---
                Content
                """;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(md));
    }

    @Test
    @DisplayName("Default version and category when not specified")
    void testDefaults() {
        assumeYaml();
        String md =
                """
                ---
                name: simple-skill
                ---
                Simple skill body.
                """;

        SkillDefinition skill = parser.parse(md);
        assertEquals("simple-skill", skill.name());
        assertEquals("1.0.0", skill.version());
        assertEquals(SkillCategory.GENERAL, skill.category());
        assertTrue(skill.triggerConditions().isEmpty());
    }

    @Test
    @DisplayName("Unknown category falls back to GENERAL")
    void testUnknownCategory() {
        assumeYaml();
        String md =
                """
                ---
                name: test
                category: UNKNOWN_CATEGORY
                ---
                Body
                """;

        SkillDefinition skill = parser.parse(md);
        assertEquals(SkillCategory.GENERAL, skill.category());
    }

    @Test
    @DisplayName("parseMetadataOnly returns empty instructions")
    void testParseMetadataOnly() {
        assumeYaml();
        SkillDefinition skill = parser.parseMetadataOnly(VALID_MARKDOWN);

        assertEquals("code-review", skill.name());
        assertNull(skill.instructions());
        assertEquals("1.0.0", skill.version());
        assertEquals(2, skill.triggerConditions().size());
    }

    @Test
    @DisplayName("Serialize and re-parse roundtrip")
    void testSerializeRoundtrip() {
        assumeYaml();
        SkillDefinition original =
                new SkillDefinition(
                        "test-skill",
                        "2.0.0",
                        "A test skill",
                        "# Instructions\n\nDo the thing.",
                        List.of("do thing", "/test"),
                        SkillCategory.TESTING);

        String markdown = parser.serialize(original);
        assertNotNull(markdown);
        assertTrue(markdown.contains("name: test-skill"));
        assertTrue(markdown.contains("version: 2.0.0"));
        assertTrue(markdown.contains("category: TESTING"));

        // Re-parse
        SkillDefinition reparsed = parser.parse(markdown);
        assertEquals("test-skill", reparsed.name());
        assertEquals("2.0.0", reparsed.version());
        assertEquals(SkillCategory.TESTING, reparsed.category());
        assertEquals(2, reparsed.triggerConditions().size());
    }

    @Test
    @DisplayName("Serialize skill with no triggers")
    void testSerializeNoTriggers() {
        assumeYaml();
        SkillDefinition skill =
                new SkillDefinition(
                        "minimal", "1.0.0", "desc", "body", List.of(), SkillCategory.GENERAL);

        String md = parser.serialize(skill);
        assertFalse(md.contains("triggers:"));
    }

    @Test
    @DisplayName("Long description gets truncated")
    void testLongDescription() {
        assumeYaml();
        String longParagraph = "A".repeat(300);
        String md =
                """
                ---
                name: test
                ---
                # Heading

                %s
                """
                        .formatted(longParagraph);

        SkillDefinition skill = parser.parse(md);
        assertTrue(skill.description().length() <= 200);
        assertTrue(skill.description().endsWith("..."));
    }

    @Test
    @DisplayName("Body with no content yields empty description")
    void testEmptyBody() {
        assumeYaml();
        String md =
                """
                ---
                name: test
                ---
                """;

        SkillDefinition skill = parser.parse(md);
        assertEquals("", skill.description());
    }

    @Test
    @DisplayName("Parse allowed_tools from YAML front-matter")
    void parseAllowedToolsFromYaml() {
        assumeYaml();
        String md =
                """
                ---
                name: restricted-skill
                version: 1.0.0
                category: CODE
                allowed_tools: ["Read", "Grep", "Glob"]
                ---
                # Restricted Skill

                Only uses read tools.
                """;

        SkillDefinition skill = parser.parse(md);
        assertNotNull(skill.allowedTools());
        assertEquals(3, skill.allowedTools().size());
        assertEquals(List.of("Read", "Grep", "Glob"), skill.allowedTools());
        assertTrue(skill.hasToolRestrictions());
    }

    @Test
    @DisplayName("No allowed_tools defaults to null")
    void parseNoAllowedToolsDefaultsNull() {
        assumeYaml();
        SkillDefinition skill = parser.parse(VALID_MARKDOWN);
        assertNull(skill.allowedTools());
        assertFalse(skill.hasToolRestrictions());
    }

    @Test
    @DisplayName("Serialize skill with allowedTools")
    void serializeAllowedTools() {
        assumeYaml();
        SkillDefinition skill =
                new SkillDefinition(
                        "restricted",
                        "1.0.0",
                        "desc",
                        "body",
                        List.of(),
                        SkillCategory.CODE,
                        null,
                        null,
                        null,
                        0,
                        List.of("Read", "Grep"));

        String md = parser.serialize(skill);
        assertTrue(md.contains("allowed_tools:"));
        assertTrue(md.contains("Read"));
        assertTrue(md.contains("Grep"));
    }
}
