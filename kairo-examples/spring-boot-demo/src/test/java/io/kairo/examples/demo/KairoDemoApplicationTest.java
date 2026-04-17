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
package io.kairo.examples.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.Agent;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.shutdown.GracefulShutdownManager;
import io.kairo.mcp.McpClientRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Integration tests validating that the Spring Boot auto-configuration works correctly.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>The application context loads without errors</li>
 *   <li>All expected Kairo beans are present and properly configured</li>
 *   <li>An agent can be created programmatically from Spring-managed beans</li>
 *   <li>MCP auto-configuration activates when kairo-mcp is on the classpath</li>
 * </ul>
 *
 * <p>No real LLM API calls are made — these are purely context-load tests.
 */
@SpringBootTest
class KairoDemoApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // If we get here, the Spring context loaded successfully
        assertThat(context).isNotNull();
    }

    @Test
    void modelProviderBeanIsPresent() {
        ModelProvider provider = context.getBean(ModelProvider.class);
        assertThat(provider).isNotNull();
        assertThat(provider.name()).isEqualTo("anthropic");
    }

    @Test
    void toolRegistryBeanIsPresent() {
        ToolRegistry registry = context.getBean(ToolRegistry.class);
        assertThat(registry).isNotNull();
    }

    @Test
    void toolExecutorBeanIsPresent() {
        ToolExecutor executor = context.getBean(ToolExecutor.class);
        assertThat(executor).isNotNull();
    }

    @Test
    void permissionGuardBeanIsPresent() {
        PermissionGuard guard = context.getBean(PermissionGuard.class);
        assertThat(guard).isNotNull();
    }

    @Test
    void memoryStoreBeanIsPresent() {
        MemoryStore store = context.getBean(MemoryStore.class);
        assertThat(store).isNotNull();
    }

    @Test
    void gracefulShutdownManagerBeanIsPresent() {
        GracefulShutdownManager manager = context.getBean(GracefulShutdownManager.class);
        assertThat(manager).isNotNull();
    }

    @Test
    void defaultAgentBeanIsPresent() {
        Agent agent = context.getBean(Agent.class);
        assertThat(agent).isNotNull();
        assertThat(agent.name()).isEqualTo("test-agent");
    }

    @Test
    void mcpClientRegistryBeanIsPresent() {
        // McpAutoConfiguration should activate since kairo-mcp is on the classpath
        McpClientRegistry registry = context.getBean(McpClientRegistry.class);
        assertThat(registry).isNotNull();
    }

    @Test
    void agentCanBeBuiltProgrammatically() {
        // Demonstrate that beans from auto-configuration can be used with AgentBuilder
        ModelProvider modelProvider = context.getBean(ModelProvider.class);
        ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);
        ToolExecutor toolExecutor = context.getBean(ToolExecutor.class);
        GracefulShutdownManager shutdownManager = context.getBean(GracefulShutdownManager.class);

        Agent customAgent =
                AgentBuilder.create()
                        .name("custom-agent")
                        .model(modelProvider)
                        .tools(toolRegistry)
                        .toolExecutor(toolExecutor)
                        .systemPrompt("You are a custom agent built programmatically.")
                        .maxIterations(5)
                        .tokenBudget(10000)
                        .modelName("test-model")
                        .shutdownManager(shutdownManager)
                        .build();

        assertThat(customAgent).isNotNull();
        assertThat(customAgent.name()).isEqualTo("custom-agent");
    }

    @Test
    void controllersAreRegistered() {
        assertThat(context.getBean(ChatController.class)).isNotNull();
        assertThat(context.getBean(StructuredOutputController.class)).isNotNull();
    }

    @Test
    void streamingChatControllerIsRegistered() {
        assertThat(context.getBean(StreamingChatController.class)).isNotNull();
    }

    @Test
    void sessionChatControllerIsRegistered() {
        assertThat(context.getBean(SessionChatController.class)).isNotNull();
    }

    @Test
    void modelSwitchControllerIsRegistered() {
        assertThat(context.getBean(ModelSwitchController.class)).isNotNull();
    }

    @Test
    void customToolControllerIsRegistered() {
        assertThat(context.getBean(CustomToolController.class)).isNotNull();
    }

    @Test
    void hookDemoControllerIsRegistered() {
        assertThat(context.getBean(HookDemoController.class)).isNotNull();
    }

    @Test
    void multiAgentControllerIsRegistered() {
        assertThat(context.getBean(MultiAgentController.class)).isNotNull();
    }

    @Test
    void permissionGuardControllerIsRegistered() {
        assertThat(context.getBean(PermissionGuardController.class)).isNotNull();
    }
}
