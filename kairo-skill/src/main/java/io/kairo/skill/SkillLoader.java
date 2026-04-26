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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Maps skill name (frontmatter) → original filename stem (without .md extension). */
    private final ConcurrentHashMap<String, String> nameToFileStem = new ConcurrentHashMap<>();

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
                            try (Stream<Path> entries = Files.list(skillDir)) {
                                List<Path> allEntries = entries.toList();

                                // Traditional single-file skills (*.md)
                                List<Path> mdFiles =
                                        allEntries.stream()
                                                .filter(p -> p.toString().endsWith(".md"))
                                                .filter(Files::isRegularFile)
                                                .toList();

                                // Bundle directories (subdirectories with SKILL.md)
                                List<Path> bundleDirs =
                                        allEntries.stream()
                                                .filter(Files::isDirectory)
                                                .filter(p -> Files.exists(p.resolve("SKILL.md")))
                                                .toList();

                                Flux<SkillDefinition> singleFileFlux =
                                        Flux.fromIterable(mdFiles)
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
                                                                        .onErrorResume(
                                                                                e -> Flux.empty()));

                                Flux<SkillDefinition> bundleFlux =
                                        Flux.fromIterable(bundleDirs)
                                                .flatMap(
                                                        dir ->
                                                                loadBundleMetadata(dir)
                                                                        .doOnError(
                                                                                e ->
                                                                                        log.warn(
                                                                                                "Failed to load bundle from {}: {}",
                                                                                                dir,
                                                                                                e
                                                                                                        .getMessage()))
                                                                        .onErrorResume(
                                                                                e -> Flux.empty()));

                                return singleFileFlux.concatWith(bundleFlux);
                            } catch (IOException e) {
                                log.error("Failed to list skill directory: {}", skillDir, e);
                                return Flux.error(e);
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Loads skills from multiple search paths with last-wins priority.
     *
     * <p>Example: given paths ["classpath:skills", "./project-skills", "~/.kairo/skills"], if
     * "code-review" exists in both classpath and ~/.kairo/skills, the ~/.kairo/skills version wins
     * (last path = highest priority).
     *
     * <p>Note: index 0 = lowest priority (loaded first, overridden by later).
     *
     * @param searchPaths ordered list of search paths (lowest priority first)
     * @return Flux of loaded skill definitions
     */
    public Flux<SkillDefinition> loadFromSearchPaths(List<String> searchPaths) {
        if (searchPaths == null || searchPaths.isEmpty()) {
            return Flux.empty();
        }
        return Flux.defer(
                        () -> {
                            Map<String, SkillDefinition> skillMap = new LinkedHashMap<>();
                            for (String searchPath : searchPaths) {
                                Path resolved = resolveSearchPath(searchPath);
                                if (resolved == null) {
                                    log.debug("Skipping unresolved search path: {}", searchPath);
                                    continue;
                                }
                                // Use loadFromDirectory and collect results synchronously
                                List<SkillDefinition> skills =
                                        loadFromDirectory(resolved).collectList().block();
                                if (skills != null) {
                                    for (SkillDefinition skill : skills) {
                                        skillMap.put(skill.name(), skill);
                                    }
                                }
                            }
                            return Flux.fromIterable(skillMap.values());
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Resolve a search path string to a filesystem Path.
     *
     * <p>Supports three prefix types:
     *
     * <ul>
     *   <li>{@code classpath:} — resolved via ClassLoader.getResource()
     *   <li>{@code ~/} — resolved relative to user home directory
     *   <li>plain path — resolved as-is
     * </ul>
     *
     * @param path the search path string
     * @return resolved Path, or null if the path does not exist
     */
    Path resolveSearchPath(String path) {
        try {
            if (path.startsWith("classpath:")) {
                var resource =
                        getClass()
                                .getClassLoader()
                                .getResource(path.substring("classpath:".length()));
                if (resource == null) return null;
                return Path.of(resource.toURI());
            } else if (path.startsWith("~/")) {
                var p = Path.of(System.getProperty("user.home")).resolve(path.substring(2));
                return Files.exists(p) ? p : null;
            }
            var p = Path.of(path);
            return Files.exists(p) ? p : null;
        } catch (Exception e) {
            log.warn("Failed to resolve search path '{}': {}", path, e.getMessage());
            return null;
        }
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
                        // Preserve the original filename for later full-content reload
                        String fileStem = path.getFileName().toString().replaceFirst("\\.md$", "");
                        nameToFileStem.put(metadataOnly.name(), fileStem);
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
        // Use the original filename stem (not the frontmatter name) to locate the file
        String fileStem = nameToFileStem.getOrDefault(skillName, skillName);

        // Check if this is a bundle skill (directory with SKILL.md)
        Path bundleDir = skillDirectory.resolve(fileStem);
        if (Files.isDirectory(bundleDir) && Files.exists(bundleDir.resolve("SKILL.md"))) {
            Path skillFile = bundleDir.resolve("SKILL.md");
            try {
                String content = Files.readString(skillFile, StandardCharsets.UTF_8);
                SkillDefinition parsed = parser.parse(content);
                SkillDefinition full = withBundleRoot(parsed, bundleDir);
                registry.register(full);
                return full;
            } catch (Exception e) {
                log.warn(
                        "Failed to reload full content for bundle skill '{}': {}",
                        skillName,
                        e.getMessage());
                return metadataOnly;
            }
        }

        Path skillFile = skillDirectory.resolve(fileStem + ".md");
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

    private Flux<SkillDefinition> loadBundleMetadata(Path bundleDir) {
        return Flux.defer(
                () -> {
                    try {
                        Path skillFile = bundleDir.resolve("SKILL.md");
                        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
                        SkillDefinition parsed = parser.parseMetadataOnly(content);
                        SkillDefinition metadataOnly = withBundleRoot(parsed, bundleDir);
                        registry.register(metadataOnly);
                        // Use directory name as file stem for later reload
                        String dirName = bundleDir.getFileName().toString();
                        nameToFileStem.put(metadataOnly.name(), dirName);
                        log.debug(
                                "Loaded bundle skill metadata: {} from {}",
                                metadataOnly.name(),
                                bundleDir);
                        return Flux.just(metadataOnly);
                    } catch (Exception e) {
                        return Flux.error(e);
                    }
                });
    }

    private SkillDefinition withBundleRoot(SkillDefinition parsed, Path bundleRoot) {
        return new SkillDefinition(
                parsed.name(),
                parsed.version(),
                parsed.description(),
                parsed.instructions(),
                parsed.triggerConditions(),
                parsed.category(),
                parsed.pathPatterns(),
                parsed.requiredTools(),
                parsed.platform(),
                parsed.matchScore(),
                parsed.allowedTools(),
                bundleRoot);
    }
}
