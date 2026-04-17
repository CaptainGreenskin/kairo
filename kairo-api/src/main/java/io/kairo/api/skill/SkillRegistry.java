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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Registry for skill definitions. */
public interface SkillRegistry {

    /**
     * Register a skill definition.
     *
     * @param skill the skill to register
     */
    void register(SkillDefinition skill);

    /**
     * Look up a skill by name.
     *
     * @param name the skill name
     * @return the skill definition, or empty if not found
     */
    Optional<SkillDefinition> get(String name);

    /**
     * List all registered skills.
     *
     * @return all skills
     */
    List<SkillDefinition> list();

    /**
     * List skills by category.
     *
     * @param category the category to filter by
     * @return matching skills
     */
    List<SkillDefinition> listByCategory(SkillCategory category);

    /**
     * Load a skill definition from a file.
     *
     * @param path the file path
     * @return a Mono emitting the loaded skill
     */
    Mono<SkillDefinition> loadFromFile(Path path);

    /**
     * Load a skill definition from a URL.
     *
     * @param url the URL to load from
     * @return a Mono emitting the loaded skill
     */
    Mono<SkillDefinition> loadFromUrl(String url);

    /**
     * Load a skill definition from a classpath resource.
     *
     * <p>Useful for bundled skills shipped inside JARs.
     *
     * @param resourcePath the classpath resource path (e.g. "skills/code-review.md")
     * @return a Mono emitting the loaded skill
     */
    default Mono<SkillDefinition> loadFromClasspath(String resourcePath) {
        return Mono.error(new UnsupportedOperationException("loadFromClasspath not implemented"));
    }
}
