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

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.skill.TriggerGuard;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
 * loading skills from local files, remote URLs (with TTL caching), and classpath resources.
 */
public class DefaultSkillRegistry implements SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillRegistry.class);

    /** Default TTL for URL-loaded skill cache entries (1 hour). */
    private static final Duration DEFAULT_URL_CACHE_TTL = Duration.ofHours(1);

    private final ConcurrentHashMap<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry> urlCache = new ConcurrentHashMap<>();
    private final TriggerGuard triggerGuard;
    private final SkillMarkdownParser parser;
    private final Duration urlCacheTtl;

    /** Create a registry with a default {@link DefaultTriggerGuard} and default TTL. */
    public DefaultSkillRegistry() {
        this(new DefaultTriggerGuard());
    }

    /**
     * Create a registry with a custom trigger guard and default TTL.
     *
     * @param triggerGuard the trigger guard for activation decisions
     */
    public DefaultSkillRegistry(TriggerGuard triggerGuard) {
        this(triggerGuard, DEFAULT_URL_CACHE_TTL);
    }

    /**
     * Create a registry with a custom trigger guard and custom cache TTL.
     *
     * @param triggerGuard the trigger guard for activation decisions
     * @param urlCacheTtl the TTL for URL-loaded skill cache entries
     */
    public DefaultSkillRegistry(TriggerGuard triggerGuard, Duration urlCacheTtl) {
        this.triggerGuard = triggerGuard;
        this.parser = new SkillMarkdownParser();
        this.urlCacheTtl = urlCacheTtl;
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
    public void unregister(String name) {
        SkillDefinition removed = skills.remove(name);
        if (removed != null) {
            log.info("Unregistered skill: {}", name);
        }
        // Remove any URL cache entries that resolved to this skill name
        urlCache.entrySet().removeIf(e -> name.equals(e.getValue().skill().name()));
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
        return Mono.defer(
                () -> {
                    // Check TTL cache first
                    CacheEntry cached = urlCache.get(url);
                    if (cached != null && !cached.isExpired(urlCacheTtl)) {
                        log.debug("URL cache hit for: {}", url);
                        return Mono.just(cached.skill());
                    }

                    return Mono.fromCallable(
                                    () -> {
                                        log.debug("Fetching skill from URL: {}", url);
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
                                                client.send(
                                                        request,
                                                        HttpResponse.BodyHandlers.ofString());
                                        if (response.statusCode() != 200) {
                                            throw new IOException(
                                                    "Failed to download skill from "
                                                            + url
                                                            + ": HTTP "
                                                            + response.statusCode());
                                        }

                                        SkillDefinition skill = parser.parse(response.body());
                                        register(skill);

                                        // Update cache
                                        urlCache.put(url, new CacheEntry(skill));
                                        log.debug(
                                                "Cached skill '{}' from URL: {}",
                                                skill.name(),
                                                url);
                                        return skill;
                                    })
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    @Override
    public Mono<SkillDefinition> loadFromClasspath(String resourcePath) {
        return Mono.fromCallable(
                        () -> {
                            InputStream is =
                                    getClass().getClassLoader().getResourceAsStream(resourcePath);
                            if (is == null) {
                                throw new IOException(
                                        "Classpath resource not found: " + resourcePath);
                            }
                            try (is) {
                                String content =
                                        new String(is.readAllBytes(), StandardCharsets.UTF_8);
                                SkillDefinition skill = parser.parse(content);
                                register(skill);
                                return skill;
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Get the current size of the URL cache.
     *
     * @return number of cached entries
     */
    int urlCacheSize() {
        return urlCache.size();
    }

    /** Clear the URL cache. Useful for testing or forcing re-fetch. */
    void clearUrlCache() {
        urlCache.clear();
    }

    /** Internal cache entry holding a skill and its load timestamp. */
    record CacheEntry(SkillDefinition skill, Instant loadedAt) {

        CacheEntry(SkillDefinition skill) {
            this(skill, Instant.now());
        }

        boolean isExpired(Duration ttl) {
            return Instant.now().isAfter(loadedAt.plus(ttl));
        }
    }
}
