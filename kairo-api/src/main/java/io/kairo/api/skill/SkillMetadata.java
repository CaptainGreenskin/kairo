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
 * Wraps a {@link SkillDefinition} with additional metadata that controls how the skill is surfaced
 * and activated.
 *
 * <p>This record exists to extend skill metadata without modifying the {@link SkillDefinition}
 * record which is marked {@code @Stable}.
 *
 * @param definition the underlying skill definition
 * @param visibility controls who can see/activate this skill
 * @param disableModelInvocation when true, the LLM cannot auto-trigger this skill; only explicit
 *     user invocation (e.g., /skill-name) works
 */
@Experimental
public record SkillMetadata(
        SkillDefinition definition, SkillVisibility visibility, boolean disableModelInvocation) {

    /** Create metadata with default visibility (VISIBLE, model invocation allowed). */
    public static SkillMetadata ofDefault(SkillDefinition definition) {
        return new SkillMetadata(definition, SkillVisibility.VISIBLE, false);
    }

    /** Shorthand for the skill name. */
    public String name() {
        return definition.name();
    }

    /** Whether this skill can be auto-triggered by the LLM. */
    public boolean isModelInvocable() {
        return !disableModelInvocation && visibility != SkillVisibility.HIDDEN;
    }

    /** Whether this skill is visible to the user in listings. */
    public boolean isUserVisible() {
        return visibility != SkillVisibility.HIDDEN;
    }
}
