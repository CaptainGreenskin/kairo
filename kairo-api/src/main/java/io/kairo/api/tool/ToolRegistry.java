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
package io.kairo.api.tool;

import io.kairo.api.Stable;
import java.util.List;
import java.util.Optional;

/** Registry for tool definitions. Supports registration, lookup, and classpath scanning. */
@Stable(value = "Tool registry SPI; shape frozen since v0.1", since = "1.0.0")
public interface ToolRegistry {

    /**
     * Register a tool definition.
     *
     * @param tool the tool definition to register
     */
    void register(ToolDefinition tool);

    /**
     * Remove a tool by name.
     *
     * @param name the tool name to remove
     */
    void unregister(String name);

    /**
     * Look up a tool by name.
     *
     * @param name the tool name
     * @return the tool definition, or empty if not found
     */
    Optional<ToolDefinition> get(String name);

    /**
     * Get all tools in the given category.
     *
     * @param category the category to filter by
     * @return the matching tool definitions
     */
    List<ToolDefinition> getByCategory(ToolCategory category);

    /**
     * Get all registered tool definitions.
     *
     * @return all tool definitions
     */
    List<ToolDefinition> getAll();

    /**
     * Scan the classpath for classes annotated with {@link Tool} and register them.
     *
     * @param basePackages the base packages to scan
     */
    void scan(String... basePackages);
}
