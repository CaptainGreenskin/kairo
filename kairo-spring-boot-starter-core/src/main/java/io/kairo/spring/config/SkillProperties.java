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
package io.kairo.spring.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Skill loading configuration ({@code kairo.skills.*}).
 *
 * <p>Controls how Markdown-based skill definitions are discovered and loaded. Skills are searched
 * in the configured paths in order (lowest priority first).
 */
@ConfigurationProperties(prefix = "kairo.skills")
public class SkillProperties {

    /**
     * Whether skill loading is enabled. When disabled, no skills are discovered or available to the
     * agent.
     *
     * <p>Default: {@code true}
     */
    private boolean enabled = true;

    /**
     * Ordered search paths for skill files (lowest priority first). Supports:
     *
     * <ul>
     *   <li>{@code classpath:} prefix for classpath resources
     *   <li>{@code ~/} prefix for user home directory
     *   <li>plain filesystem paths
     * </ul>
     *
     * <p>Default: {@code ["classpath:skills"]}
     */
    private List<String> searchPaths = List.of("classpath:skills");

    /**
     * Whether skills are read-only (disallow create/edit/delete via skill management tools). Set to
     * {@code true} in production to prevent agents from modifying skill definitions.
     *
     * <p>Default: {@code false}
     */
    private boolean readonly = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getSearchPaths() {
        return searchPaths;
    }

    public void setSearchPaths(List<String> searchPaths) {
        this.searchPaths = searchPaths;
    }

    public boolean isReadonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }
}
