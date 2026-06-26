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
package io.kairo.multiagent.subagent;

/**
 * Declares the workspace context slices an expert role requires from the orchestrator. Used by the
 * generator to filter SharedContext before injection.
 */
public enum ContextScope {
    /** Complete workspace tree (full depth). */
    FULL_TREE,
    /** Depth-limited tree summary (top 2 levels only). */
    TREE_SUMMARY,
    /** Key project files (README, build configs, etc.). */
    KEY_FILES,
    /** Relevant source code files. */
    SOURCE_FILES,
    /** Test files and test patterns. */
    TEST_FILES,
    /** Only outputs from upstream steps (no workspace context injection). */
    UPSTREAM_ONLY,
    /** Everything available in SharedContext. */
    ALL
}
