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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class EvolutionConfigTest {

    @Test
    @DisplayName("DISABLED constant has enabled=false and null SPI fields")
    void disabledConstant() {
        EvolutionConfig cfg = EvolutionConfig.DISABLED;

        assertFalse(cfg.enabled());
        assertNull(cfg.evolutionPolicy());
        assertNull(cfg.evolvedSkillStore());
        assertNull(cfg.evolutionTrigger());
        assertNull(cfg.reviewModelName());
        assertEquals(0, cfg.iterationThreshold());
    }

    @Test
    @DisplayName("Builder with no setters produces sensible defaults")
    void builderDefaults() {
        EvolutionConfig cfg = EvolutionConfig.builder().build();

        assertFalse(cfg.enabled());
        assertEquals(8, cfg.iterationThreshold());
        assertNull(cfg.evolutionPolicy());
        assertNull(cfg.evolvedSkillStore());
        assertNull(cfg.evolutionTrigger());
        assertNull(cfg.reviewModelName());
    }

    @Test
    @DisplayName("Builder with all fields set produces correct config")
    void builderWithAllFields() {
        EvolutionPolicy policy = ctx -> Mono.just(EvolutionOutcome.empty());
        EvolvedSkillStore store =
                new EvolvedSkillStore() {
                    @Override
                    public Mono<EvolvedSkill> save(EvolvedSkill skill) {
                        return Mono.just(skill);
                    }

                    @Override
                    public Mono<Optional<EvolvedSkill>> get(String name) {
                        return Mono.just(Optional.empty());
                    }

                    @Override
                    public Flux<EvolvedSkill> list() {
                        return Flux.empty();
                    }

                    @Override
                    public Mono<Void> delete(String name) {
                        return Mono.empty();
                    }
                };
        EvolutionTrigger trigger =
                new EvolutionTrigger() {
                    @Override
                    public boolean shouldReviewSkill(EvolutionContext ctx) {
                        return false;
                    }

                    @Override
                    public boolean shouldReviewMemory(EvolutionContext ctx) {
                        return false;
                    }
                };

        EvolutionConfig cfg =
                EvolutionConfig.builder()
                        .evolutionPolicy(policy)
                        .evolvedSkillStore(store)
                        .evolutionTrigger(trigger)
                        .enabled(true)
                        .iterationThreshold(5)
                        .reviewModelName("gpt-4o")
                        .build();

        assertTrue(cfg.enabled());
        assertEquals(5, cfg.iterationThreshold());
        assertSame(policy, cfg.evolutionPolicy());
        assertSame(store, cfg.evolvedSkillStore());
        assertSame(trigger, cfg.evolutionTrigger());
        assertEquals("gpt-4o", cfg.reviewModelName());
    }

    @Test
    @DisplayName("enabled=true config returns true from enabled()")
    void enabledCheck() {
        EvolutionConfig cfg = EvolutionConfig.builder().enabled(true).build();

        assertTrue(cfg.enabled());
    }
}
