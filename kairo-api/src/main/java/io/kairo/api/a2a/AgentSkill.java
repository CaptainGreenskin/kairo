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
package io.kairo.api.a2a;

import java.util.List;

/**
 * Declares a distinct capability that an agent can perform.
 *
 * <p>Maps to the A2A Protocol {@code AgentSkill} type. Fields are a pragmatic subset of the full
 * spec: id, name, description, and tags. Additional A2A fields (examples, inputModes, outputModes,
 * securityRequirements) can be carried through the enclosing {@link AgentCard}'s metadata map until
 * a dedicated compatibility module is introduced.
 *
 * @param id unique skill identifier (e.g. "weather_query")
 * @param name human-readable skill name
 * @param description what the skill does
 * @param tags categorization tags for discovery; may be empty
 * @see AgentCard
 */
public record AgentSkill(String id, String name, String description, List<String> tags) {

    /** Compact constructor with null-safe defaults. */
    public AgentSkill {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (description == null) {
            throw new IllegalArgumentException("description must not be null");
        }
        tags = tags != null ? List.copyOf(tags) : List.of();
    }
}
