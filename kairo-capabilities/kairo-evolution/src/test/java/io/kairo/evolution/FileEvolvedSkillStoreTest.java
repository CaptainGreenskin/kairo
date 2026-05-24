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
package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillTrustLevel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileEvolvedSkillStoreTest {

    @TempDir Path tempDir;
    private FileEvolvedSkillStore store;

    @BeforeEach
    void setUp() {
        store = new FileEvolvedSkillStore(tempDir.resolve("evolved-skills"));
    }

    private EvolvedSkill createSkill(String name) {
        return new EvolvedSkill(
                name,
                "1.0",
                "Test skill: " + name,
                "When user asks about " + name + ", do the thing.",
                "general",
                Set.of("test"),
                SkillTrustLevel.DRAFT,
                Map.of("source", "test"),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                0);
    }

    @Test
    void saveAndGet() {
        EvolvedSkill skill = createSkill("test-skill");
        store.save(skill).block();

        Optional<EvolvedSkill> loaded = store.get("test-skill").block();
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo("test-skill");
        assertThat(loaded.get().instructions()).contains("do the thing");
        assertThat(loaded.get().trustLevel()).isEqualTo(SkillTrustLevel.DRAFT);
    }

    @Test
    void getMissing() {
        Optional<EvolvedSkill> loaded = store.get("nonexistent").block();
        assertThat(loaded).isEmpty();
    }

    @Test
    void listReturnsAll() {
        store.save(createSkill("skill-a")).block();
        store.save(createSkill("skill-b")).block();

        var skills = store.list().collectList().block();
        assertThat(skills).hasSize(2);
    }

    @Test
    void deleteRemovesSkill() {
        store.save(createSkill("to-delete")).block();
        store.delete("to-delete").block();

        assertThat(store.get("to-delete").block()).isEmpty();
    }

    @Test
    void deleteNonexistentDoesNotThrow() {
        store.delete("nonexistent").block();
    }

    @Test
    void persistsAcrossInstances() {
        store.save(createSkill("persistent-skill")).block();

        FileEvolvedSkillStore store2 = new FileEvolvedSkillStore(tempDir.resolve("evolved-skills"));
        Optional<EvolvedSkill> loaded = store2.get("persistent-skill").block();
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo("persistent-skill");
    }

    @Test
    void overwriteExistingSkill() {
        store.save(createSkill("overwrite-me")).block();

        EvolvedSkill updated =
                new EvolvedSkill(
                        "overwrite-me",
                        "2.0",
                        "Updated",
                        "New instructions here",
                        "general",
                        Set.of("updated"),
                        SkillTrustLevel.VALIDATED,
                        null,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-06-01T00:00:00Z"),
                        5);
        store.save(updated).block();

        Optional<EvolvedSkill> loaded = store.get("overwrite-me").block();
        assertThat(loaded).isPresent();
        assertThat(loaded.get().version()).isEqualTo("2.0");
        assertThat(loaded.get().trustLevel()).isEqualTo(SkillTrustLevel.VALIDATED);
        assertThat(loaded.get().instructions()).isEqualTo("New instructions here");
    }

    @Test
    void serializeDeserializeRoundTrip() {
        EvolvedSkill original =
                new EvolvedSkill(
                        "round-trip",
                        "1.2.3",
                        "A skill with\nnewlines",
                        "Instructions with\nmultiple lines\nand special chars: =, \\",
                        "coding",
                        Set.of("java", "testing"),
                        SkillTrustLevel.TRUSTED,
                        Map.of("author", "test", "priority", "high"),
                        Instant.parse("2026-03-15T10:30:00Z"),
                        Instant.parse("2026-04-20T15:45:00Z"),
                        42);

        String serialized = FileEvolvedSkillStore.serialize(original);
        EvolvedSkill deserialized = FileEvolvedSkillStore.deserialize(serialized);

        assertThat(deserialized.name()).isEqualTo(original.name());
        assertThat(deserialized.version()).isEqualTo(original.version());
        assertThat(deserialized.instructions()).isEqualTo(original.instructions());
        assertThat(deserialized.trustLevel()).isEqualTo(original.trustLevel());
        assertThat(deserialized.usageCount()).isEqualTo(original.usageCount());
        assertThat(deserialized.createdAt()).isEqualTo(original.createdAt());
    }

    @Test
    void sanitizeNameRemovesSpecialChars() {
        assertThat(FileEvolvedSkillStore.sanitizeName("My Skill!")).isEqualTo("my_skill_");
        assertThat(FileEvolvedSkillStore.sanitizeName("hello-world_v2"))
                .isEqualTo("hello-world_v2");
    }

    @Test
    void listEmptyDirectoryReturnsEmpty() {
        var skills = store.list().collectList().block();
        assertThat(skills).isEmpty();
    }

    @Test
    void directoryReturnsConfiguredPath() {
        assertThat(store.directory()).isEqualTo(tempDir.resolve("evolved-skills"));
    }
}
