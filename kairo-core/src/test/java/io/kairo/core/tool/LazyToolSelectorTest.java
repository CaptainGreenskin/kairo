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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LazyToolSelectorTest {

    private static ToolDefinition tool(String name, String description, ToolCategory category) {
        return new ToolDefinition(name, description, category, null, LazyToolSelectorTest.class);
    }

    private static ToolRegistry registryOf(ToolDefinition... tools) {
        List<ToolDefinition> list = List.of(tools);
        return new ToolRegistry() {
            @Override
            public void register(ToolDefinition tool) {}

            @Override
            public void unregister(String name) {}

            @Override
            public Optional<ToolDefinition> get(String name) {
                return list.stream().filter(t -> t.name().equals(name)).findFirst();
            }

            @Override
            public List<ToolDefinition> getByCategory(ToolCategory category) {
                return list.stream().filter(t -> t.category() == category).toList();
            }

            @Override
            public List<ToolDefinition> getAll() {
                return list;
            }

            @Override
            public void scan(String... basePackages) {}
        };
    }

    @Test
    void baselineToolsAlwaysIncluded() {
        ToolDefinition readTool = tool("read", "Read a file", ToolCategory.FILE_AND_CODE);
        ToolDefinition bashTool = tool("bash", "Execute shell commands", ToolCategory.EXECUTION);
        ToolDefinition webSearch = tool("web_search", "Search the web", ToolCategory.INFORMATION);
        ToolDefinition cronCreate = tool("cron_create", "Schedule a task", ToolCategory.SCHEDULING);

        ToolRegistry registry = registryOf(readTool, bashTool, webSearch, cronCreate);
        LazyToolSelector selector = new LazyToolSelector(registry);

        List<ToolDefinition> selected = selector.select("schedule a cron job", 2);

        assertThat(selected).extracting(ToolDefinition::name).contains("read", "bash");
    }

    @Test
    void relevantNonBaselineToolsIncluded() {
        ToolDefinition readTool = tool("read", "Read a file", ToolCategory.FILE_AND_CODE);
        ToolDefinition cronCreate =
                tool("cron_create", "Schedule a cron job", ToolCategory.SCHEDULING);
        ToolDefinition cronDelete =
                tool("cron_delete", "Delete a cron job", ToolCategory.SCHEDULING);
        ToolDefinition webSearch =
                tool("web_search", "Search the web for information", ToolCategory.INFORMATION);

        ToolRegistry registry = registryOf(readTool, cronCreate, cronDelete, webSearch);
        LazyToolSelector selector = new LazyToolSelector(registry);

        List<ToolDefinition> selected = selector.select("schedule a cron job", 5);

        assertThat(selected).extracting(ToolDefinition::name).contains("cron_create");
    }

    @Test
    void maxToolsLimitsNonBaseline() {
        ToolDefinition readTool = tool("read", "Read a file", ToolCategory.FILE_AND_CODE);
        ToolDefinition tool1 = tool("search", "Search code", ToolCategory.INFORMATION);
        ToolDefinition tool2 = tool("web", "Search web", ToolCategory.INFORMATION);
        ToolDefinition tool3 = tool("memory", "Read memory", ToolCategory.AGENT_AND_TASK);

        ToolRegistry registry = registryOf(readTool, tool1, tool2, tool3);
        LazyToolSelector selector = new LazyToolSelector(registry);

        List<ToolDefinition> selected = selector.select("search code and web", 1);

        long nonBaseline =
                selected.stream()
                        .filter(
                                t ->
                                        t.category() != ToolCategory.FILE_AND_CODE
                                                && t.category() != ToolCategory.EXECUTION)
                        .count();
        assertThat(nonBaseline).isLessThanOrEqualTo(1);
    }

    @Test
    void emptyPromptReturnsAll() {
        ToolDefinition t1 = tool("a", "Tool A", ToolCategory.GENERAL);
        ToolDefinition t2 = tool("b", "Tool B", ToolCategory.GENERAL);

        ToolRegistry registry = registryOf(t1, t2);
        LazyToolSelector selector = new LazyToolSelector(registry);

        List<ToolDefinition> selected = selector.select("", 5);
        assertThat(selected).hasSize(2);
    }

    @Test
    void nullPromptReturnsAll() {
        ToolDefinition t1 = tool("a", "Tool A", ToolCategory.GENERAL);

        ToolRegistry registry = registryOf(t1);
        LazyToolSelector selector = new LazyToolSelector(registry);

        List<ToolDefinition> selected = selector.select(null, 5);
        assertThat(selected).hasSize(1);
    }

    @Test
    void emptyRegistryReturnsEmpty() {
        ToolRegistry registry = registryOf();
        LazyToolSelector selector = new LazyToolSelector(registry);

        assertThat(selector.select("anything", 5)).isEmpty();
    }

    @Test
    void irrelevantToolsFilteredOut() {
        ToolDefinition readTool = tool("read", "Read a file", ToolCategory.FILE_AND_CODE);
        ToolDefinition webSearch =
                tool("web_search", "Search the web for information", ToolCategory.INFORMATION);
        ToolDefinition cronCreate =
                tool("cron_create", "Schedule a cron job", ToolCategory.SCHEDULING);

        ToolRegistry registry = registryOf(readTool, webSearch, cronCreate);
        LazyToolSelector selector = new LazyToolSelector(registry);

        List<ToolDefinition> selected =
                selector.select("search the web for documentation about React", 5);

        assertThat(selected).extracting(ToolDefinition::name).contains("read", "web_search");
    }

    @Test
    void defaultMaxToolsUsed() {
        ToolDefinition t1 = tool("a", "Tool A for testing", ToolCategory.GENERAL);

        ToolRegistry registry = registryOf(t1);
        LazyToolSelector selector = new LazyToolSelector(registry);

        List<ToolDefinition> selected = selector.select("testing");
        assertThat(selected).isNotEmpty();
    }

    @Test
    void noDuplicateTools() {
        ToolDefinition readTool = tool("read", "Read a file", ToolCategory.FILE_AND_CODE);
        ToolDefinition readTool2 = tool("read", "Read a file again", ToolCategory.FILE_AND_CODE);

        ToolRegistry registry = registryOf(readTool, readTool2);
        LazyToolSelector selector = new LazyToolSelector(registry);

        List<ToolDefinition> selected = selector.select("read file", 5);
        long readCount = selected.stream().filter(t -> t.name().equals("read")).count();
        assertThat(readCount).isEqualTo(1);
    }
}
