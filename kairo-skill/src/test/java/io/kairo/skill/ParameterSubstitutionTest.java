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

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Parameter Substitution")
class ParameterSubstitutionTest {

    @Test
    @DisplayName("Basic arg replacement: {{name}} → value")
    void basicArgReplacement() {
        String result =
                SkillMarkdownParser.substituteParameters("Hello {{name}}", Map.of("name", "World"));
        assertEquals("Hello World", result);
    }

    @Test
    @DisplayName("Multiple placeholders: {{a}} and {{b}} → substituted")
    void multiplePlaceholders() {
        String result =
                SkillMarkdownParser.substituteParameters(
                        "{{a}} and {{b}}", Map.of("a", "X", "b", "Y"));
        assertEquals("X and Y", result);
    }

    @Test
    @DisplayName("Unmatched placeholder → empty string")
    void unmatchedPlaceholderBecomesEmpty() {
        // Args map is non-empty but doesn't contain the referenced key
        String result =
                SkillMarkdownParser.substituteParameters(
                        "Hello {{missing}}", Map.of("other", "value"));
        assertEquals("Hello ", result);
    }

    @Test
    @DisplayName("${SKILL_DIR} resolution in content")
    void skillDirResolution() {
        Path bundleRoot = Path.of("/home/user/skills/my-skill");
        String content = "Run ${SKILL_DIR}/scripts/run.sh";
        // ${SKILL_DIR} substitution is done in SkillLoadTool, simulating here:
        String result = content.replace("${SKILL_DIR}", bundleRoot.toAbsolutePath().toString());
        assertTrue(result.contains("/scripts/run.sh"));
        assertFalse(result.contains("${SKILL_DIR}"));
    }

    @Test
    @DisplayName("Substitution order: ${SKILL_DIR} before {{arg}} — arg values work correctly")
    void substitutionOrder() {
        Path bundleRoot = Path.of("/home/user/skills/my-skill");
        String content = "Path: ${SKILL_DIR}/data, Name: {{name}}";

        // Step 1: ${SKILL_DIR} substitution
        content = content.replace("${SKILL_DIR}", bundleRoot.toAbsolutePath().toString());
        // Step 2: {{arg}} substitution
        content = SkillMarkdownParser.substituteParameters(content, Map.of("name", "test-skill"));

        assertTrue(content.contains("/data"));
        assertTrue(content.contains("test-skill"));
        assertFalse(content.contains("${SKILL_DIR}"));
        assertFalse(content.contains("{{name}}"));
    }

    @Test
    @DisplayName("Null args: content returned unchanged")
    void nullArgs() {
        String content = "Hello {{name}}";
        String result = SkillMarkdownParser.substituteParameters(content, null);
        assertEquals(content, result);
    }

    @Test
    @DisplayName("Empty args: content returned unchanged")
    void emptyArgs() {
        String content = "Hello {{name}}";
        String result = SkillMarkdownParser.substituteParameters(content, Collections.emptyMap());
        assertEquals(content, result);
    }

    @Test
    @DisplayName("Null content: returns null")
    void nullContent() {
        assertNull(SkillMarkdownParser.substituteParameters(null, Map.of("key", "val")));
    }

    @Test
    @DisplayName("Special characters in values handled correctly")
    void specialCharactersInValues() {
        String result =
                SkillMarkdownParser.substituteParameters(
                        "{{path}}", Map.of("path", "C:\\Users\\test$dir"));
        assertEquals("C:\\Users\\test$dir", result);
    }

    @Test
    @DisplayName("No placeholders: content returned unchanged")
    void noPlaceholders() {
        String content = "Plain content without any placeholders or ${variables}";
        String result = SkillMarkdownParser.substituteParameters(content, Map.of("key", "val"));
        assertEquals(content, result);
    }

    @Test
    @DisplayName("Mixed matched and unmatched placeholders")
    void mixedMatchedAndUnmatched() {
        String result =
                SkillMarkdownParser.substituteParameters(
                        "{{found}} and {{missing}}", Map.of("found", "here"));
        assertEquals("here and ", result);
    }
}
