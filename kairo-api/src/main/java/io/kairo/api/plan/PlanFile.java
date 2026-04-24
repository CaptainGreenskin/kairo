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
package io.kairo.api.plan;

import io.kairo.api.Stable;
import java.time.Instant;

/**
 * Represents a plan file managed by the agent.
 *
 * @param id unique identifier for the plan
 * @param name the plan name
 * @param content the plan content
 * @param createdAt when the plan was created
 * @param status the current plan status
 */
@Stable(value = "Plan file record; shape frozen since v0.1", since = "1.0.0")
public record PlanFile(
        String id, String name, String content, Instant createdAt, PlanStatus status) {}
