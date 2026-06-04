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
import java.util.Map;

/**
 * Request to create a new team. Extensible via new fields without breaking the {@link
 * TeamManager#create(TeamCreateRequest)} signature.
 */
@Experimental("Team creation request; introduced in v1.2.0")
public record TeamCreateRequest(String name, String goal, Map<String, Object> metadata) {

    public TeamCreateRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        goal = goal == null ? "" : goal;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static TeamCreateRequest of(String name, String goal) {
        return new TeamCreateRequest(name, goal, Map.of());
    }

    public static TeamCreateRequest ofName(String name) {
        return new TeamCreateRequest(name, "", Map.of());
    }
}
