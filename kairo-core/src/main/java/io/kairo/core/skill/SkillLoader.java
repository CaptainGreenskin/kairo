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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Loads skills from the filesystem with progressive disclosure support.
 *
 * <p>Progressive disclosure means skills are loaded in stages:
 *
 * <ol>
 *   <li>Metadata only (name, description, triggers) — for listing
 *   <li>Full content (including instructions) — on demand when activated
 * </ol>
 *
 * <p>This prevents all skill instructions from being dumped into the system prompt, keeping the
 * context window clean (anti-pollution).
 */
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    private final SkillRegistry registry;
    private final SkillMarkdownParser parser;

    /** Directory from which skills were loaded (for full-content reload). */
    private Path skillDirectory;

    public SkillLoader(SkillRegistry registry) {
        this.registry = registry;
        this.parser = new SkillMarkdownParser();
    }

    /**
     * Load all {@code *.md} skill files from a directory. Only metadata is registered; full
     * instructions are loaded on demand.
     *
     * @param skillDir the directory containing skill Markdown files
     * @return a Flux emitting each loaded skill (metadata only)
     */
    public Flux<SkillDefinition> loadFromDirectory(Path skillDir) {
        this.skillDirectory = skillDir;
        return Flux.defer(
                        () -> {
                            if (!Files.isDirectory(skillDir)) {
                                log.warn("Skill directory does not exist: {}", skillDir);
                                return Flux.empty();
                            }
                            try (Stream<Path> files = Files.list(skillDir)) {
                                List<Path> mdFiles =
                                        files.filter(p -> p.toString().endsWith(".md"))
                                                .filter(Files::isRegularFile)
                                                .toList();

                                return Flux.fromIterable(mdFiles)
                                        .flatMap(
                                                path ->
                                                        loadMetadata(path)
                                                                .doOnError(
                                                                        e ->
                                                                                log.warn(
                                                                                        "Failed to load skill from {}: {}",
                                                                                        path,
                                                                                        e
                                                                                                .getMessage()))
                                                                .onErrorResume(e -> Flux.empty()));
                            } catch (IOException e) {
                                log.error("Failed to list skill directory: {}", skillDir, e);
                                return Flux.error(e);
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * List all distinct categories of registered skills.
     *
     * @return category names
     */
    public List<String> listCategories() {
        return registry.list().stream().map(s -> s.category().name()).distinct().sorted().toList();
    }

    /**
     * List skills in a given category (metadata only, no instructions).
     *
     * @param category the category to filter by
     * @return skills with metadata only
     */
    public List<SkillDefinition> listByCategory(SkillCategory category) {
        return registry.listByCategory(category).stream()
                .map(SkillDefinition::metadataOnly)
                .toList();
    }

    /**
     * Get the full content of a skill, including instructions. If the skill was loaded
     * metadata-only, this reloads the full file.
     *
     * @param skillName the skill name
     * @return the full skill definition, or null if not found
     */
    public SkillDefinition getFullContent(String skillName) {
        return registry.get(skillName)
                .map(
                        skill -> {
                            // If instructions are already loaded, return as-is
                            if (skill.hasInstructions()) {
                                return skill;
                            }
                            // Otherwise, try to reload from the skill directory
                            return reloadFull(skillName, skill);
                        })
                .orElse(null);
    }

    /**
     * List all available skill categories as an enum list.
     *
     * @return all categories that have at least one registered skill
     */
    public List<SkillCategory> listSkillCategories() {
        return Arrays.stream(SkillCategory.values())
                .filter(cat -> !registry.listByCategory(cat).isEmpty())
                .toList();
    }

    private Flux<SkillDefinition> loadMetadata(Path path) {
        return Flux.defer(
                () -> {
                    try {
                        String content = Files.readString(path, StandardCharsets.UTF_8);
                        // Progressive disclosure: register metadata-only, load full on demand
                        SkillDefinition metadataOnly = parser.parseMetadataOnly(content);
                        registry.register(metadataOnly);
                        log.debug("Loaded skill metadata: {} from {}", metadataOnly.name(), path);
                        return Flux.just(metadataOnly);
                    } catch (Exception e) {
                        return Flux.error(e);
                    }
                });
    }

    private SkillDefinition reloadFull(String skillName, SkillDefinition metadataOnly) {
        if (skillDirectory == null) {
            return metadataOnly;
        }
        // Try to find the file by skill name
        Path skillFile = skillDirectory.resolve(skillName + ".md");
        if (!Files.exists(skillFile)) {
            log.warn(
                    "Cannot reload full content for skill '{}': file not found at {}",
                    skillName,
                    skillFile);
            return metadataOnly;
        }
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            SkillDefinition full = parser.parse(content);
            registry.register(full); // update registry with full content
            return full;
        } catch (Exception e) {
            log.warn("Failed to reload full content for skill '{}': {}", skillName, e.getMessage());
            return metadataOnly;
        }
    }
}
