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
package io.kairo.core.skill;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.skill.TriggerGuard;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default implementation of {@link SkillRegistry}.
 *
 * <p>Maintains an in-memory registry of skills backed by a {@link ConcurrentHashMap}. Supports
 * loading skills from local files and remote URLs.
 */
public class DefaultSkillRegistry implements SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillRegistry.class);

    private final ConcurrentHashMap<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final TriggerGuard triggerGuard;
    private final SkillMarkdownParser parser;

    /** Create a registry with a default {@link DefaultTriggerGuard}. */
    public DefaultSkillRegistry() {
        this(new DefaultTriggerGuard());
    }

    /**
     * Create a registry with a custom trigger guard.
     *
     * @param triggerGuard the trigger guard for activation decisions
     */
    public DefaultSkillRegistry(TriggerGuard triggerGuard) {
        this.triggerGuard = triggerGuard;
        this.parser = new SkillMarkdownParser();
    }

    /**
     * Get the trigger guard used by this registry.
     *
     * @return the trigger guard
     */
    public TriggerGuard triggerGuard() {
        return triggerGuard;
    }

    @Override
    public void register(SkillDefinition skill) {
        if (skill == null || skill.name() == null || skill.name().isBlank()) {
            throw new IllegalArgumentException("Skill and skill name must not be null or blank");
        }
        skills.put(skill.name(), skill);
        log.info(
                "Registered skill: {} (v{}, category={})",
                skill.name(),
                skill.version(),
                skill.category());
    }

    @Override
    public Optional<SkillDefinition> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    @Override
    public List<SkillDefinition> list() {
        return List.copyOf(skills.values());
    }

    @Override
    public List<SkillDefinition> listByCategory(SkillCategory category) {
        return skills.values().stream().filter(s -> s.category() == category).toList();
    }

    @Override
    public Mono<SkillDefinition> loadFromFile(Path path) {
        return Mono.fromCallable(
                        () -> {
                            if (!Files.exists(path)) {
                                throw new IOException("Skill file not found: " + path);
                            }
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            SkillDefinition skill = parser.parse(content);
                            register(skill);
                            return skill;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<SkillDefinition> loadFromUrl(String url) {
        return Mono.fromCallable(
                        () -> {
                            HttpClient client =
                                    HttpClient.newBuilder()
                                            .connectTimeout(Duration.ofSeconds(10))
                                            .build();
                            HttpRequest request =
                                    HttpRequest.newBuilder()
                                            .uri(URI.create(url))
                                            .timeout(Duration.ofSeconds(30))
                                            .GET()
                                            .build();

                            HttpResponse<String> response =
                                    client.send(request, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() != 200) {
                                throw new IOException(
                                        "Failed to download skill from "
                                                + url
                                                + ": HTTP "
                                                + response.statusCode());
                            }

                            SkillDefinition skill = parser.parse(response.body());
                            register(skill);
                            return skill;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
