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
package io.kairo.core.agent;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.McpCapabilityConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SkillToolManagerTest {

    private ToolExecutor toolExecutor;
    private AgentConfig configWithNoMcp;

    @BeforeEach
    void setUp() {
        toolExecutor = mock(ToolExecutor.class);
        configWithNoMcp =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(mock(ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(5)
                        .tokenBudget(100_000)
                        .mcpCapability(McpCapabilityConfig.EMPTY)
                        .build();
    }

    @Test
    void initMcpWithNoServersCompletesEmpty() {
        SkillToolManager manager = new SkillToolManager(configWithNoMcp, toolExecutor);
        StepVerifier.create(manager.initMcpIfConfigured()).verifyComplete();
    }

    @Test
    void initMcpWithEmptyServerListCompletesEmpty() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("agent")
                        .modelProvider(mock(ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(3)
                        .tokenBudget(50_000)
                        .mcpCapability(
                                new McpCapabilityConfig(java.util.List.of(), 128, true, null))
                        .build();
        SkillToolManager manager = new SkillToolManager(config, toolExecutor);
        StepVerifier.create(manager.initMcpIfConfigured()).verifyComplete();
    }

    @Test
    void initMcpIsIdempotentWhenCalledTwice() {
        SkillToolManager manager = new SkillToolManager(configWithNoMcp, toolExecutor);
        // first call initializes
        StepVerifier.create(manager.initMcpIfConfigured()).verifyComplete();
        // second call is a no-op — must also complete without error
        StepVerifier.create(manager.initMcpIfConfigured()).verifyComplete();
    }

    @Test
    void clearSkillRestrictionsDelegatesToToolExecutor() {
        SkillToolManager manager = new SkillToolManager(configWithNoMcp, toolExecutor);
        manager.clearSkillRestrictions();
        verify(toolExecutor).clearAllowedTools();
    }

    @Test
    void closeMcpRegistryWithNullPluginDoesNotThrow() {
        SkillToolManager manager = new SkillToolManager(configWithNoMcp, toolExecutor);
        // mcpRegistryPlugin is null before any MCP servers are connected
        assertThatCode(manager::closeMcpRegistry).doesNotThrowAnyException();
    }

    @Test
    void closeMcpRegistryWithAutoCloseablePluginClosesIt() throws Exception {
        SkillToolManager manager = new SkillToolManager(configWithNoMcp, toolExecutor);
        // Use a concrete closeable that tracks whether close() was called
        var closed = new boolean[] {false};
        AutoCloseable fakePlugin = () -> closed[0] = true;
        var field = SkillToolManager.class.getDeclaredField("mcpRegistryPlugin");
        field.setAccessible(true);
        field.set(manager, fakePlugin);

        assertThatCode(manager::closeMcpRegistry).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(closed[0]).isTrue();
    }
}
