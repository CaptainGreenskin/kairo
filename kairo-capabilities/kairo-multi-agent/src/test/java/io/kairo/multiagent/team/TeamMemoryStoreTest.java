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
package io.kairo.multiagent.team;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.core.memory.structured.MemoryFile;
import io.kairo.core.memory.structured.MemoryType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamMemoryStoreTest {

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        TeamMemoryStore.clearInstances();
    }

    @AfterEach
    void tearDown() {
        TeamMemoryStore.clearInstances();
    }

    @Test
    void forTeamReturnsSameInstance() {
        TeamMemoryStore store1 = TeamMemoryStore.forTeam(tempDir, "alpha");
        TeamMemoryStore store2 = TeamMemoryStore.forTeam(tempDir, "alpha");
        assertThat(store1).isSameAs(store2);
    }

    @Test
    void forTeamReturnsDifferentInstancesPerTeam() {
        TeamMemoryStore store1 = TeamMemoryStore.forTeam(tempDir, "alpha");
        TeamMemoryStore store2 = TeamMemoryStore.forTeam(tempDir, "beta");
        assertThat(store1).isNotSameAs(store2);
    }

    @Test
    void writeAndRead() {
        TeamMemoryStore store = TeamMemoryStore.forTeam(tempDir, "team1");
        MemoryFile file =
                new MemoryFile(
                        "finding-1",
                        "Auth module finding",
                        MemoryType.PROJECT,
                        "XSS in login",
                        Instant.now());
        store.write(file);

        MemoryFile read = store.read("finding-1");
        assertThat(read).isNotNull();
        assertThat(read.name()).isEqualTo("finding-1");
        assertThat(read.body()).isEqualTo("XSS in login");
    }

    @Test
    void readNonExistent() {
        TeamMemoryStore store = TeamMemoryStore.forTeam(tempDir, "team2");
        assertThat(store.read("nonexistent")).isNull();
    }

    @Test
    void deleteExisting() {
        TeamMemoryStore store = TeamMemoryStore.forTeam(tempDir, "team3");
        store.write(
                new MemoryFile("to-delete", "desc", MemoryType.FEEDBACK, "body", Instant.now()));

        assertThat(store.delete("to-delete")).isTrue();
        assertThat(store.read("to-delete")).isNull();
    }

    @Test
    void deleteNonExistent() {
        TeamMemoryStore store = TeamMemoryStore.forTeam(tempDir, "team4");
        assertThat(store.delete("nonexistent")).isFalse();
    }

    @Test
    void listAll() {
        TeamMemoryStore store = TeamMemoryStore.forTeam(tempDir, "team5");
        store.write(new MemoryFile("mem-a", "desc-a", MemoryType.PROJECT, "body-a", Instant.now()));
        store.write(new MemoryFile("mem-b", "desc-b", MemoryType.USER, "body-b", Instant.now()));

        List<MemoryFile> all = store.listAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(MemoryFile::name).containsExactlyInAnyOrder("mem-a", "mem-b");
    }

    @Test
    void searchFindsRelevant() {
        TeamMemoryStore store = TeamMemoryStore.forTeam(tempDir, "team6");
        store.write(
                new MemoryFile(
                        "db-perf",
                        "Database performance issue",
                        MemoryType.PROJECT,
                        "Slow queries on users table",
                        Instant.now()));
        store.write(
                new MemoryFile(
                        "api-design",
                        "REST API guidelines",
                        MemoryType.FEEDBACK,
                        "Follow RESTful conventions",
                        Instant.now()));

        List<MemoryFile> results = store.search("database performance", 5);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).name()).isEqualTo("db-perf");
    }

    @Test
    void teamNameAccessor() {
        TeamMemoryStore store = TeamMemoryStore.forTeam(tempDir, "my-team");
        assertThat(store.teamName()).isEqualTo("my-team");
    }

    @Test
    void memoryDirPath() {
        TeamMemoryStore store = TeamMemoryStore.forTeam(tempDir, "path-team");
        assertThat(store.memoryDir().toString()).contains(".kairo/teams/path-team/memory");
    }

    @Test
    void teamsAreIsolated() {
        TeamMemoryStore storeA = TeamMemoryStore.forTeam(tempDir, "iso-a");
        TeamMemoryStore storeB = TeamMemoryStore.forTeam(tempDir, "iso-b");

        storeA.write(
                new MemoryFile(
                        "shared-name", "A only", MemoryType.PROJECT, "A body", Instant.now()));

        assertThat(storeA.read("shared-name")).isNotNull();
        assertThat(storeB.read("shared-name")).isNull();
    }
}
