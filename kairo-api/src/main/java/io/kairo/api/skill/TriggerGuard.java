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

import io.kairo.api.Stable;

/**
 * Guards against unintended skill activation (prompt pollution prevention).
 *
 * <p>Ensures that a skill is only activated when the user's input matches the skill's trigger
 * conditions with sufficiently high confidence.
 */
@Stable(value = "Skill trigger guard SPI; shape frozen since v0.5", since = "1.0.0")
public interface TriggerGuard {

    /**
     * Determine whether a skill should be activated for the given input.
     *
     * @param userInput the user's input text
     * @param skill the skill definition to evaluate
     * @return true if the skill should be activated
     */
    boolean shouldActivate(String userInput, SkillDefinition skill);

    /**
     * The minimum confidence threshold required for activation (0.0 to 1.0).
     *
     * @return the confidence threshold
     */
    float confidenceThreshold();
}
