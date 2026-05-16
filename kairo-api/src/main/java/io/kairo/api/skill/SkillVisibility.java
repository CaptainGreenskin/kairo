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
package io.kairo.api.skill;

import io.kairo.api.Experimental;

/**
 * Controls how a skill is surfaced to the agent.
 *
 * <p>Visibility acts as a gate: the skill's trigger conditions still apply, but only within the
 * scope allowed by its visibility level.
 */
@Experimental
public enum SkillVisibility {

    /** Visible to both the LLM and the user. Default. */
    VISIBLE,

    /** Only activatable by explicit user invocation (e.g., {@code /skill-name}). */
    USER_ONLY,

    /** Registered but not surfaced — used for internal/programmatic activation. */
    HIDDEN
}
