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
package io.kairo.core.tool;

import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolRegistry;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ToolRegistry} backed by concurrent hash maps.
 *
 * <p>Supports manual registration, classpath scanning via {@link AnnotationToolScanner}, and tool
 * instance management for execution.
 */
public class DefaultToolRegistry implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultToolRegistry.class);

    private final ConcurrentHashMap<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> toolInstances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> toolMetadata =
            new ConcurrentHashMap<>();
    private final AnnotationToolScanner scanner = new AnnotationToolScanner();

    @Override
    public void register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
        log.info("Registered tool: {} [{}]", tool.name(), tool.category());
    }

    @Override
    public void unregister(String name) {
        tools.remove(name);
        toolInstances.remove(name);
        toolMetadata.remove(name);
        log.info("Unregistered tool: {}", name);
    }

    @Override
    public Optional<ToolDefinition> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public List<ToolDefinition> getByCategory(ToolCategory category) {
        return tools.values().stream().filter(t -> t.category() == category).toList();
    }

    @Override
    public List<ToolDefinition> getAll() {
        return List.copyOf(tools.values());
    }

    @Override
    public void scan(String... basePackages) {
        List<ToolDefinition> definitions = scanner.scan(basePackages);
        for (ToolDefinition def : definitions) {
            register(def);
            // Auto-create instance if implementationClass is a ToolHandler
            if (ToolHandler.class.isAssignableFrom(def.implementationClass())
                    && !toolInstances.containsKey(def.name())) {
                try {
                    Object instance =
                            def.implementationClass().getDeclaredConstructor().newInstance();
                    registerInstance(def.name(), instance);
                } catch (Exception e) {
                    log.warn("Could not auto-instantiate tool {}: {}", def.name(), e.getMessage());
                }
            }
        }
        log.info(
                "Scanned {} tool(s) from packages: {}",
                definitions.size(),
                String.join(", ", basePackages));
    }

    /**
     * Get the tool handler instance for the given tool name.
     *
     * @param name the tool name
     * @return the tool handler instance, or {@code null} if not found
     */
    public Object getToolInstance(String name) {
        return toolInstances.get(name);
    }

    /**
     * Register a tool handler instance for the given tool name.
     *
     * @param name the tool name
     * @param instance the handler instance
     */
    public void registerInstance(String name, Object instance) {
        toolInstances.put(name, instance);
    }

    /**
     * Register a tool class by scanning its annotations and auto-creating an instance.
     *
     * @param toolClass the annotated tool class
     */
    public void registerTool(Class<? extends ToolHandler> toolClass) {
        ToolDefinition def = scanner.scanClass(toolClass);
        register(def);
        try {
            ToolHandler instance = toolClass.getDeclaredConstructor().newInstance();
            registerInstance(def.name(), instance);
        } catch (Exception e) {
            log.error(
                    "Failed to instantiate tool class {}: {}", toolClass.getName(), e.getMessage());
        }
    }

    /**
     * Set metadata for a tool. Metadata is passed to guardrail policies via {@link
     * io.kairo.api.guardrail.GuardrailContext#metadata()}.
     *
     * @param name the tool name
     * @param metadata the metadata map (defensively copied)
     */
    public void setToolMetadata(String name, Map<String, Object> metadata) {
        toolMetadata.put(name, new HashMap<>(metadata));
    }

    /**
     * Get metadata for a tool.
     *
     * @param name the tool name
     * @return an unmodifiable metadata map, or an empty map if none is set
     */
    public Map<String, Object> getToolMetadata(String name) {
        Map<String, Object> meta = toolMetadata.get(name);
        return meta != null ? Collections.unmodifiableMap(meta) : Collections.emptyMap();
    }
}
