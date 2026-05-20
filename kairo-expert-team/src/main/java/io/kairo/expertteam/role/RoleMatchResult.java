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
package io.kairo.expertteam.role;

/**
 * Result of matching a role against a task's features.
 *
 * @param roleId the role identifier
 * @param score relevance score in [0.0, 1.0]; higher = better match
 * @param profile the matched expert profile
 */
public record RoleMatchResult(String roleId, double score, ExpertProfile profile)
        implements Comparable<RoleMatchResult> {

    @Override
    public int compareTo(RoleMatchResult other) {
        return Double.compare(other.score, this.score); // descending
    }
}
