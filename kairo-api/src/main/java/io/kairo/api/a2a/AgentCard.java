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
import java.util.Map;

/**
 * Self-describing manifest for an agent in the A2A Protocol.
 *
 * <p>Fields are aligned with the Google A2A Protocol {@code AgentCard} specification. The core
 * fields ({@code name}, {@code description}, {@code version}, {@code skills}, {@code streaming})
 * map directly to their A2A counterparts. The {@code metadata} map carries additional A2A fields
 * (provider, documentationUrl, iconUrl, securitySchemes, etc.) and can be used for
 * framework-specific extensions.
 *
 * <p>For in-process use the {@code id} field serves as the unique agent identifier. When HTTP
 * transport is added (v0.5+), {@code id} maps to the first {@code supportedInterfaces} entry.
 *
 * @param id unique agent identifier
 * @param name human-readable agent name
 * @param description brief description of the agent's purpose
 * @param version agent version string
 * @param tags capability tags for discovery; may be empty
 * @param streaming whether the agent supports streaming responses
 * @param skills declared skills; may be empty
 * @param metadata extensible metadata (A2A compatibility fields, framework extensions)
 * @see AgentSkill
 * @see AgentCardResolver
 */
public record AgentCard(
        String id,
        String name,
        String description,
        String version,
        List<String> tags,
        boolean streaming,
        List<AgentSkill> skills,
        Map<String, Object> metadata) {

    /** Compact constructor with null-safe defaults. */
    public AgentCard {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (description == null) {
            description = "";
        }
        if (version == null || version.isBlank()) {
            version = "1.0.0";
        }
        tags = tags != null ? List.copyOf(tags) : List.of();
        skills = skills != null ? List.copyOf(skills) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Convenience factory for a minimal agent card.
     *
     * @param id agent identifier
     * @param name agent name
     * @param description agent description
     * @return a new AgentCard with default values
     */
    public static AgentCard of(String id, String name, String description) {
        return new AgentCard(id, name, description, "1.0.0", List.of(), false, List.of(), Map.of());
    }
}
