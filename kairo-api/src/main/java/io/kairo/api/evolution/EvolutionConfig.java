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
import javax.annotation.Nullable;

/**
 * Configuration for the self-evolution subsystem.
 *
 * <p>Use the {@link Builder} to construct instances, or {@link #DISABLED} for a no-op default.
 *
 * @param evolutionPolicy the policy for reviewing evolution outcomes
 * @param evolvedSkillStore the store for persisting evolved skills
 * @param evolutionTrigger the trigger for deciding when to review
 * @param enabled whether evolution is active
 * @param iterationThreshold minimum iterations before evolution triggers
 * @param reviewModelName optional model name override for evolution review calls
 * @since v0.9 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change in v0.10")
public record EvolutionConfig(
        @Nullable EvolutionPolicy evolutionPolicy,
        @Nullable EvolvedSkillStore evolvedSkillStore,
        @Nullable EvolutionTrigger evolutionTrigger,
        boolean enabled,
        int iterationThreshold,
        @Nullable String reviewModelName) {

    public static final EvolutionConfig DISABLED =
            new EvolutionConfig(null, null, null, false, 0, null);

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EvolutionPolicy evolutionPolicy;
        private EvolvedSkillStore evolvedSkillStore;
        private EvolutionTrigger evolutionTrigger;
        private boolean enabled = false;
        private int iterationThreshold = 8;
        private String reviewModelName;

        private Builder() {}

        public Builder evolutionPolicy(EvolutionPolicy policy) {
            this.evolutionPolicy = policy;
            return this;
        }

        public Builder evolvedSkillStore(EvolvedSkillStore store) {
            this.evolvedSkillStore = store;
            return this;
        }

        public Builder evolutionTrigger(EvolutionTrigger trigger) {
            this.evolutionTrigger = trigger;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder iterationThreshold(int threshold) {
            this.iterationThreshold = threshold;
            return this;
        }

        public Builder reviewModelName(String name) {
            this.reviewModelName = name;
            return this;
        }

        public EvolutionConfig build() {
            return new EvolutionConfig(
                    evolutionPolicy,
                    evolvedSkillStore,
                    evolutionTrigger,
                    enabled,
                    iterationThreshold,
                    reviewModelName);
        }
    }
}
