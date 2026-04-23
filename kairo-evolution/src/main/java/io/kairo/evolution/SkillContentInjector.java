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
package io.kairo.evolution;

import io.kairo.api.agent.SystemPromptContributor;
import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTrustLevel;
import reactor.core.publisher.Mono;

/**
 * Injects evolved skills into the agent's system prompt as a dynamic section.
 *
 * <p>Implements {@link SystemPromptContributor} so the framework can discover and include this
 * section during prompt construction without coupling to {@code SystemPromptBuilder} in kairo-core.
 *
 * <p>Only skills with trust level {@link SkillTrustLevel#VALIDATED} or higher are included.
 *
 * @since v0.9 (Experimental)
 */
public class SkillContentInjector implements SystemPromptContributor {

    private final EvolvedSkillStore skillStore;

    /**
     * Create a new injector.
     *
     * @param skillStore the store to read evolved skills from
     */
    public SkillContentInjector(EvolvedSkillStore skillStore) {
        this.skillStore = skillStore;
    }

    @Override
    public String sectionName() {
        return "evolved-skills";
    }

    @Override
    public Mono<String> content() {
        return skillStore
                .listByMinTrust(SkillTrustLevel.VALIDATED)
                .map(this::formatSkill)
                .collectList()
                .map(skills -> skills.isEmpty() ? "" : String.join("\n\n", skills));
    }

    private String formatSkill(EvolvedSkill skill) {
        return "### " + skill.name() + "\n" + skill.instructions();
    }
}
