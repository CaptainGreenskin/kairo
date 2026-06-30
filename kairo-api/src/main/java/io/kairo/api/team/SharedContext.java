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
package io.kairo.api.team;

import io.kairo.api.Experimental;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable shared workspace context injected by the orchestrator into expert agents.
 *
 * <p>Captures a point-in-time snapshot of the workspace structure, key file contents, and
 * extensible metadata so that downstream agents can orient themselves without redundant I/O.
 *
 * @param workspaceTree output of tree command (depth-limited); non-null
 * @param keyFiles path → file content for important files; defensively copied, never {@code null}
 * @param projectSummary brief project description; non-null (may be empty)
 * @param metadata extensible free-form metadata; defensively copied, never {@code null}
 * @param gatheredAt timestamp for staleness checks; non-null
 * @since v0.11 (Experimental)
 */
@Experimental("Shared workspace context for orchestrator injection; v0.11")
public record SharedContext(
        String workspaceTree,
        Map<String, String> keyFiles,
        String projectSummary,
        Map<String, Object> metadata,
        Instant gatheredAt) {

    public SharedContext {
        Objects.requireNonNull(workspaceTree, "workspaceTree must not be null");
        keyFiles = Map.copyOf(Objects.requireNonNull(keyFiles, "keyFiles must not be null"));
        Objects.requireNonNull(projectSummary, "projectSummary must not be null");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
        Objects.requireNonNull(gatheredAt, "gatheredAt must not be null");
    }

    /** Returns an empty {@link SharedContext} with no workspace data and a current timestamp. */
    public static SharedContext empty() {
        return new SharedContext("", Map.of(), "", Map.of(), Instant.now());
    }

    /** Fluent builder for constructing {@link SharedContext} instances. */
    public static final class Builder {

        private String workspaceTree = "";
        private Map<String, String> keyFiles = Map.of();
        private String projectSummary = "";
        private Map<String, Object> metadata = Map.of();
        private Instant gatheredAt = Instant.now();

        public Builder workspaceTree(String workspaceTree) {
            this.workspaceTree = workspaceTree;
            return this;
        }

        public Builder keyFiles(Map<String, String> keyFiles) {
            this.keyFiles = keyFiles;
            return this;
        }

        public Builder projectSummary(String projectSummary) {
            this.projectSummary = projectSummary;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder gatheredAt(Instant gatheredAt) {
            this.gatheredAt = gatheredAt;
            return this;
        }

        public SharedContext build() {
            return new SharedContext(workspaceTree, keyFiles, projectSummary, metadata, gatheredAt);
        }
    }
}
