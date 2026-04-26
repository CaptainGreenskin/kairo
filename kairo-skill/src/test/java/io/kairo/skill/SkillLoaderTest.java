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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class SkillLoaderTest {

    private static final String SKILL_MD =
            """
            ---
            name: test-skill
            version: 1.0.0
            category: CODE
            triggers:
              - "run tests"
            ---
            # Test Skill

            Instructions for testing.
            """;

    @TempDir Path tempDir;

    private SkillLoader loader;
    private DefaultSkillRegistry registry;

    @BeforeEach
    void setup() {
        registry = new DefaultSkillRegistry();
        loader = new SkillLoader(registry);
    }

    @Test
    void loadFromDirectory_nonExistentDir_completesEmpty() {
        Path missing = tempDir.resolve("no-such-dir");
        StepVerifier.create(loader.loadFromDirectory(missing)).verifyComplete();
    }

    @Test
    void loadFromDirectory_emptyDir_completesEmpty() {
        StepVerifier.create(loader.loadFromDirectory(tempDir)).verifyComplete();
    }

    @Test
    void loadFromDirectory_withValidSkill_emitsSkill() throws Exception {
        Files.writeString(tempDir.resolve("test-skill.md"), SKILL_MD, StandardCharsets.UTF_8);
        StepVerifier.create(loader.loadFromDirectory(tempDir))
                .assertNext(skill -> assertThat(skill.name()).isEqualTo("test-skill"))
                .verifyComplete();
    }

    @Test
    void loadFromDirectory_registersSkillInRegistry() throws Exception {
        Files.writeString(tempDir.resolve("test-skill.md"), SKILL_MD, StandardCharsets.UTF_8);
        loader.loadFromDirectory(tempDir).blockLast();
        assertThat(registry.get("test-skill")).isPresent();
    }

    @Test
    void loadFromSearchPaths_nullList_completesEmpty() {
        StepVerifier.create(loader.loadFromSearchPaths(null)).verifyComplete();
    }

    @Test
    void loadFromSearchPaths_emptyList_completesEmpty() {
        StepVerifier.create(loader.loadFromSearchPaths(List.of())).verifyComplete();
    }

    @Test
    void listCategories_emptyRegistry_returnsEmptyList() {
        assertThat(loader.listCategories()).isEmpty();
    }

    @Test
    void resolveSearchPath_nonExistentPath_returnsNull() {
        assertThat(loader.resolveSearchPath("/this/does/not/exist/xyz")).isNull();
    }

    @Test
    void resolveSearchPath_nonExistentClasspath_returnsNull() {
        assertThat(loader.resolveSearchPath("classpath:no/such/resource")).isNull();
    }

    @Test
    void getFullContent_unknownSkill_returnsNull() {
        assertThat(loader.getFullContent("unknown-skill")).isNull();
    }

    @Test
    void listSkillCategories_emptyRegistry_returnsEmpty() {
        assertThat(loader.listSkillCategories()).isEmpty();
    }
}
