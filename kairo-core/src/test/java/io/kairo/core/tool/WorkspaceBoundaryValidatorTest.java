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
package io.kairo.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WorkspaceBoundaryValidatorTest {

    private static final Path WORKSPACE = Path.of("/tmp/project");

    // ── validate() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate() — path boundary checks")
    class Validate {

        @Test
        void relativePathInsideWorkspace() {
            Optional<String> result =
                    WorkspaceBoundaryValidator.validate("src/Main.java", WORKSPACE);
            assertThat(result).isEmpty();
        }

        @Test
        void pathTraversalBlocked() {
            Optional<String> result =
                    WorkspaceBoundaryValidator.validate("../../../etc/passwd", WORKSPACE);
            assertThat(result).isPresent();
        }

        @Test
        void absolutePathOutsideWorkspace() {
            Optional<String> result = WorkspaceBoundaryValidator.validate("/etc/shadow", WORKSPACE);
            assertThat(result).isPresent();
        }

        @Test
        void dotSlashRelativePathInsideWorkspace() {
            Optional<String> result =
                    WorkspaceBoundaryValidator.validate("./src/Main.java", WORKSPACE);
            assertThat(result).isEmpty();
        }

        @Test
        void blankPathSkipped() {
            Optional<String> result = WorkspaceBoundaryValidator.validate("", WORKSPACE);
            assertThat(result).isEmpty();
        }

        @Test
        void nullPathSkipped() {
            Optional<String> result = WorkspaceBoundaryValidator.validate(null, WORKSPACE);
            assertThat(result).isEmpty();
        }
    }

    // ── extractPaths() ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractPaths() — argument extraction")
    class ExtractPaths {

        @Test
        void extractsPathKey() {
            List<String> paths = WorkspaceBoundaryValidator.extractPaths(Map.of("path", "foo.txt"));
            assertThat(paths).containsExactly("foo.txt");
        }

        @Test
        void extractsFilePathKey() {
            List<String> paths =
                    WorkspaceBoundaryValidator.extractPaths(Map.of("filePath", "bar.txt"));
            assertThat(paths).containsExactly("bar.txt");
        }

        @Test
        void extractsTargetPathKey() {
            List<String> paths =
                    WorkspaceBoundaryValidator.extractPaths(Map.of("targetPath", "baz.txt"));
            assertThat(paths).containsExactly("baz.txt");
        }

        @Test
        void ignoresNonPathKeys() {
            List<String> paths = WorkspaceBoundaryValidator.extractPaths(Map.of("command", "ls"));
            assertThat(paths).isEmpty();
        }

        @Test
        void extractsFromFilesList() {
            List<Map<String, Object>> files =
                    List.of(Map.of("path", "a.txt"), Map.of("path", "b.txt"));
            Map<String, Object> args = Map.of("files", files);
            List<String> paths = WorkspaceBoundaryValidator.extractPaths(args);
            assertThat(paths).containsExactly("a.txt", "b.txt");
        }
    }
}
