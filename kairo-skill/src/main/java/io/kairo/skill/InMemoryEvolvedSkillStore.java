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
package io.kairo.skill;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTrustLevel;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * In-memory implementation of {@link EvolvedSkillStore} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Uses {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} for atomic
 * version-aware updates: a newer (or equal) version replaces an older one.
 *
 * <p>State is lost on JVM restart — suitable for development and testing.
 *
 * @since v0.9 (Experimental)
 */
public class InMemoryEvolvedSkillStore implements EvolvedSkillStore {

    private final ConcurrentHashMap<String, EvolvedSkill> skills = new ConcurrentHashMap<>();

    @Override
    public Mono<EvolvedSkill> save(EvolvedSkill skill) {
        return Mono.fromCallable(
                () ->
                        skills.compute(
                                skill.name(),
                                (key, existing) -> {
                                    if (existing == null) return skill;
                                    if (compareVersions(skill.version(), existing.version()) >= 0) {
                                        return skill;
                                    }
                                    return existing;
                                }));
    }

    @Override
    public Mono<Optional<EvolvedSkill>> get(String name) {
        return Mono.fromCallable(() -> Optional.ofNullable(skills.get(name)));
    }

    @Override
    public Flux<EvolvedSkill> list() {
        return Flux.fromIterable(skills.values());
    }

    @Override
    public Mono<Void> delete(String name) {
        return Mono.fromRunnable(() -> skills.remove(name));
    }

    @Override
    public Flux<EvolvedSkill> listByMinTrust(SkillTrustLevel minLevel) {
        return Flux.fromIterable(skills.values())
                .filter(s -> s.trustLevel().level() >= minLevel.level());
    }

    /**
     * Simple semantic version comparison. Splits on "." and compares integer parts left-to-right.
     * Non-numeric or missing parts are treated as 0.
     *
     * @return negative if a &lt; b, 0 if equal, positive if a &gt; b
     */
    static int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int maxLen = Math.max(aParts.length, bParts.length);

        for (int i = 0; i < maxLen; i++) {
            int aVal = i < aParts.length ? parseIntSafe(aParts[i]) : 0;
            int bVal = i < bParts.length ? parseIntSafe(bParts[i]) : 0;
            if (aVal != bVal) return Integer.compare(aVal, bVal);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
