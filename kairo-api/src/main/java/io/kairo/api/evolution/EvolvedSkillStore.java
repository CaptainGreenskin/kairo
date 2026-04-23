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
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SPI for persisting and retrieving evolved skills.
 *
 * <p>Implementations may store skills in a database, file system, or remote service.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change in v0.10")
public interface EvolvedSkillStore {

    Mono<EvolvedSkill> save(EvolvedSkill skill);

    Mono<Optional<EvolvedSkill>> get(String name);

    Flux<EvolvedSkill> list();

    Mono<Void> delete(String name);

    default Flux<EvolvedSkill> listByMinTrust(SkillTrustLevel minLevel) {
        return list().filter(s -> s.trustLevel().level() >= minLevel.level());
    }
}
