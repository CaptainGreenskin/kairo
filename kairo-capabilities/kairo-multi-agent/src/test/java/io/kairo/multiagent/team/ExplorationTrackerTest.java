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
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplorationTrackerTest {

    @TempDir Path tempDir;

    private TeamMemoryStore store;
    private ExplorationTracker tracker;

    @BeforeEach
    void setUp() {
        TeamMemoryStore.clearInstances();
        store = TeamMemoryStore.forTeam(tempDir, "tracker-team");
        tracker = new ExplorationTracker(store);
    }

    @AfterEach
    void tearDown() {
        TeamMemoryStore.clearInstances();
    }

    @Test
    void markAndCheckExplored() {
        assertThat(tracker.isExplored("src/main/java/AuthService.java")).isFalse();

        tracker.markExplored("src/main/java/AuthService.java", "No issues found");

        assertThat(tracker.isExplored("src/main/java/AuthService.java")).isTrue();
    }

    @Test
    void getFinding() {
        tracker.markExplored("src/main/java/LoginController.java", "XSS vulnerability");

        MemoryFile finding = tracker.getFinding("src/main/java/LoginController.java");
        assertThat(finding).isNotNull();
        assertThat(finding.body()).contains("XSS vulnerability");
        assertThat(finding.body()).contains("LoginController.java");
    }

    @Test
    void getFindingNotExplored() {
        assertThat(tracker.getFinding("nonexistent/File.java")).isNull();
    }

    @Test
    void emptyFindingDefaultsToNoNotableFindings() {
        tracker.markExplored("src/main/java/Util.java", "");

        MemoryFile finding = tracker.getFinding("src/main/java/Util.java");
        assertThat(finding).isNotNull();
        assertThat(finding.body()).contains("No notable findings");
    }

    @Test
    void allExploredPaths() {
        tracker.markExplored("src/A.java", "ok");
        tracker.markExplored("src/B.java", "ok");

        Set<String> paths = tracker.allExploredPaths();
        assertThat(paths).hasSize(2);
    }

    @Test
    void allExploredPathsEmpty() {
        assertThat(tracker.allExploredPaths()).isEmpty();
    }

    @Test
    void clearExplored() {
        tracker.markExplored("src/C.java", "temp");
        assertThat(tracker.isExplored("src/C.java")).isTrue();

        assertThat(tracker.clearExplored("src/C.java")).isTrue();
        assertThat(tracker.isExplored("src/C.java")).isFalse();
    }

    @Test
    void clearExploredNonExistent() {
        assertThat(tracker.clearExplored("never/explored.java")).isFalse();
    }

    @Test
    void updateExploration() {
        tracker.markExplored("src/D.java", "First pass");
        tracker.markExplored("src/D.java", "Second pass with more detail");

        MemoryFile finding = tracker.getFinding("src/D.java");
        assertThat(finding).isNotNull();
        assertThat(finding.body()).contains("Second pass");
    }

    // -- toSlug tests --

    @Test
    void toSlugBasicPath() {
        String slug = ExplorationTracker.toSlug("src/main/java/Foo.java");
        assertThat(slug).startsWith("explored-");
        assertThat(slug).doesNotContain("/");
        assertThat(slug).doesNotContain(".");
    }

    @Test
    void toSlugWindowsPath() {
        String slug = ExplorationTracker.toSlug("src\\main\\java\\Foo.java");
        assertThat(slug).startsWith("explored-");
        assertThat(slug).doesNotContain("\\");
    }

    @Test
    void toSlugLongPathTruncated() {
        String longPath = "a/".repeat(100) + "File.java";
        String slug = ExplorationTracker.toSlug(longPath);
        assertThat(slug.length()).isLessThanOrEqualTo(80 + "explored-".length());
    }

    @Test
    void toSlugConsistentForSamePath() {
        String slug1 = ExplorationTracker.toSlug("src/main/java/Bar.java");
        String slug2 = ExplorationTracker.toSlug("src/main/java/Bar.java");
        assertThat(slug1).isEqualTo(slug2);
    }
}
