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

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class SkillLoaderTest {

    @TempDir Path tempDir;

    private DefaultSkillRegistry registry;
    private SkillLoader loader;

    @BeforeEach
    void setUp() {
        registry = new DefaultSkillRegistry();
        loader = new SkillLoader(registry);
    }

    private static String skillMd(String name, String description, String... triggers) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(name).append('\n');
        sb.append("version: 1.0.0\n");
        sb.append("category: CODE\n");
        sb.append("description: ").append(description).append('\n');
        sb.append("triggers:\n");
        for (String trigger : triggers) {
            sb.append("  - \"").append(trigger).append("\"\n");
        }
        sb.append("---\n");
        sb.append("## Instructions\n");
        sb.append("Do the thing described in: ").append(description).append('\n');
        return sb.toString();
    }

    @Test
    void loadFromDirectoryEmptyDirReturnsEmpty() {
        StepVerifier.create(loader.loadFromDirectory(tempDir)).verifyComplete();
    }

    @Test
    void loadFromDirectoryNonExistentPathReturnsEmpty() {
        Path missing = tempDir.resolve("does-not-exist");
        StepVerifier.create(loader.loadFromDirectory(missing)).verifyComplete();
    }

    @Test
    void loadFromDirectoryParsesNameFromFrontmatter() throws IOException {
        Files.writeString(tempDir.resolve("my-skill.md"), skillMd("my-skill", "desc", "/my"));

        StepVerifier.create(loader.loadFromDirectory(tempDir))
                .assertNext(skill -> assertThat(skill.name()).isEqualTo("my-skill"))
                .verifyComplete();
    }

    @Test
    void loadFromDirectoryParsesDescription() throws IOException {
        Files.writeString(
                tempDir.resolve("code-review.md"),
                skillMd("code-review", "Reviews code quality", "/review"));

        StepVerifier.create(loader.loadFromDirectory(tempDir))
                .assertNext(skill -> assertThat(skill.description()).isNotBlank())
                .verifyComplete();
    }

    @Test
    void loadFromDirectoryParsesTriggers() throws IOException {
        Files.writeString(
                tempDir.resolve("deploy.md"),
                skillMd("deploy", "Deploy app", "/deploy", "deploy to production"));

        StepVerifier.create(loader.loadFromDirectory(tempDir))
                .assertNext(
                        skill ->
                                assertThat(skill.triggerConditions())
                                        .containsExactlyInAnyOrder(
                                                "/deploy", "deploy to production"))
                .verifyComplete();
    }

    @Test
    void loadFromDirectoryRegistersSkillInRegistry() throws IOException {
        Files.writeString(tempDir.resolve("alpha.md"), skillMd("alpha", "Alpha skill", "/alpha"));

        loader.loadFromDirectory(tempDir).blockLast();

        assertThat(registry.get("alpha")).isPresent();
    }

    @Test
    void loadFromDirectoryLoadsMultipleSkills() throws IOException {
        Files.writeString(tempDir.resolve("skill-a.md"), skillMd("skill-a", "A", "/a"));
        Files.writeString(tempDir.resolve("skill-b.md"), skillMd("skill-b", "B", "/b"));

        List<SkillDefinition> skills = loader.loadFromDirectory(tempDir).collectList().block();

        assertThat(skills).hasSize(2);
        assertThat(skills)
                .extracting(SkillDefinition::name)
                .containsExactlyInAnyOrder("skill-a", "skill-b");
    }

    @Test
    void loadFromDirectoryIgnoresNonMdFiles() throws IOException {
        Files.writeString(tempDir.resolve("readme.txt"), "not a skill");
        Files.writeString(tempDir.resolve("skill.md"), skillMd("real-skill", "Real", "/real"));

        List<SkillDefinition> skills = loader.loadFromDirectory(tempDir).collectList().block();

        assertThat(skills).hasSize(1);
        assertThat(skills.get(0).name()).isEqualTo("real-skill");
    }

    @Test
    void loadedSkillMetadataOnlyHasNullOrBlankInstructions() throws IOException {
        Files.writeString(tempDir.resolve("writer.md"), skillMd("writer", "Writes text", "/write"));

        List<SkillDefinition> skills = loader.loadFromDirectory(tempDir).collectList().block();

        assertThat(skills).hasSize(1);
        // Metadata-only: instructions should be null (progressive disclosure)
        assertThat(skills.get(0).instructions()).isNull();
    }

    @Test
    void getFullContentReloadsInstructions() throws IOException {
        Files.writeString(tempDir.resolve("full.md"), skillMd("full", "Full skill", "/full"));

        loader.loadFromDirectory(tempDir).blockLast();

        SkillDefinition full = loader.getFullContent("full");
        assertThat(full).isNotNull();
        assertThat(full.instructions()).isNotBlank();
    }

    @Test
    void getFullContentReturnsNullForUnknownSkill() {
        SkillDefinition result = loader.getFullContent("nonexistent");
        assertThat(result).isNull();
    }

    @Test
    void listCategoriesReturnsCategoriesOfRegisteredSkills() throws IOException {
        Files.writeString(tempDir.resolve("code-skill.md"), skillMd("code-skill", "Code", "/c"));

        loader.loadFromDirectory(tempDir).blockLast();

        List<String> categories = loader.listCategories();
        assertThat(categories).contains(SkillCategory.CODE.name());
    }

    @Test
    void listByCategoryFiltersCorrectly() throws IOException {
        Files.writeString(tempDir.resolve("coder.md"), skillMd("coder", "Coder skill", "/code"));

        loader.loadFromDirectory(tempDir).blockLast();

        List<SkillDefinition> codeSkills = loader.listByCategory(SkillCategory.CODE);
        assertThat(codeSkills).extracting(SkillDefinition::name).contains("coder");
    }
}
