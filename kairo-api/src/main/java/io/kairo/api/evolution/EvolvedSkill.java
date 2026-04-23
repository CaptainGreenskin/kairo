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
package io.kairo.api.evolution;

import io.kairo.api.Experimental;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Represents a skill that has been created or refined through the self-evolution process.
 *
 * @param name unique skill name
 * @param version version identifier
 * @param description human-readable description
 * @param instructions the skill instructions used during prompt injection
 * @param category optional category for grouping
 * @param tags tags for filtering and discovery
 * @param trustLevel the current trust level of this skill
 * @param metadata extensible key-value pairs
 * @param createdAt when the skill was first created
 * @param updatedAt when the skill was last modified
 * @param usageCount number of times the skill has been applied
 * @since v0.9 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change in v0.10")
public record EvolvedSkill(
        String name,
        String version,
        String description,
        String instructions,
        String category,
        Set<String> tags,
        SkillTrustLevel trustLevel,
        @Nullable Map<String, String> metadata,
        Instant createdAt,
        Instant updatedAt,
        long usageCount) {

    public EvolvedSkill {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(instructions, "instructions must not be null");
        tags = tags != null ? Set.copyOf(tags) : Set.of();
        metadata = metadata != null ? Map.copyOf(metadata) : null;
    }

    public EvolvedSkill withUpdatedInstructions(String newInstructions) {
        return new EvolvedSkill(
                name,
                version,
                description,
                newInstructions,
                category,
                tags,
                trustLevel,
                metadata,
                createdAt,
                Instant.now(),
                usageCount);
    }

    public EvolvedSkill withUsageIncrement() {
        return new EvolvedSkill(
                name,
                version,
                description,
                instructions,
                category,
                tags,
                trustLevel,
                metadata,
                createdAt,
                Instant.now(),
                usageCount + 1);
    }
}
