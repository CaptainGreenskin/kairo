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
package io.kairo.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.core.agent.DefaultAgentFactory;
import io.kairo.core.memory.FileMemoryStore;
import io.kairo.core.memory.InMemoryStore;
import io.kairo.core.model.AnthropicProvider;
import io.kairo.core.shutdown.GracefulShutdownManager;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.spring.AgentActuatorAutoConfiguration;
import io.kairo.spring.AgentRuntimeAutoConfiguration;
import io.kairo.spring.AgentRuntimeProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Integration tests for {@link AgentRuntimeAutoConfiguration} verifying that auto-configuration
 * activates correctly, binds properties, creates conditional beans, and allows user overrides.
 */
@Tag("integration")
class AutoConfigurationIT {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(AgentRuntimeAutoConfiguration.class));

    // ---- Auto-configuration activation ----

    @Test
    void autoConfigurationActivatesWhenAgentClassOnClasspath() {
        runner.withPropertyValues(
                        "kairo.model.provider=anthropic", "kairo.model.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentRuntimeProperties.class);
                    assertThat(context).hasSingleBean(ToolRegistry.class);
                    assertThat(context).hasSingleBean(PermissionGuard.class);
                    assertThat(context).hasSingleBean(ToolExecutor.class);
                    assertThat(context).hasSingleBean(AgentFactory.class);
                    assertThat(context).hasSingleBean(MemoryStore.class);
                    assertThat(context).hasSingleBean(GracefulShutdownManager.class);
                    assertThat(context).hasSingleBean(ModelProvider.class);
                    assertThat(context).hasSingleBean(Agent.class);
                });
    }

    // ---- Property binding ----

    @Test
    void modelPropertiesBindCorrectly() {
        runner.withPropertyValues(
                        "kairo.model.provider=anthropic",
                        "kairo.model.api-key=sk-test-123",
                        "kairo.model.model-name=claude-test",
                        "kairo.model.max-tokens=4096",
                        "kairo.model.temperature=0.5")
                .run(context -> {
                    AgentRuntimeProperties props = context.getBean(AgentRuntimeProperties.class);
                    assertThat(props.getModel().getProvider()).isEqualTo("anthropic");
                    assertThat(props.getModel().getApiKey()).isEqualTo("sk-test-123");
                    assertThat(props.getModel().getModelName()).isEqualTo("claude-test");
                    assertThat(props.getModel().getMaxTokens()).isEqualTo(4096);
                    assertThat(props.getModel().getTemperature()).isEqualTo(0.5);
                });
    }

    @Test
    void agentPropertiesBindCorrectly() {
        runner.withPropertyValues(
                        "kairo.model.api-key=test-key",
                        "kairo.agent.name=custom-agent",
                        "kairo.agent.system-prompt=Be helpful",
                        "kairo.agent.max-iterations=100",
                        "kairo.agent.timeout-seconds=3600",
                        "kairo.agent.token-budget=500000")
                .run(context -> {
                    AgentRuntimeProperties props = context.getBean(AgentRuntimeProperties.class);
                    assertThat(props.getAgent().getName()).isEqualTo("custom-agent");
                    assertThat(props.getAgent().getSystemPrompt()).isEqualTo("Be helpful");
                    assertThat(props.getAgent().getMaxIterations()).isEqualTo(100);
                    assertThat(props.getAgent().getTimeoutSeconds()).isEqualTo(3600);
                    assertThat(props.getAgent().getTokenBudget()).isEqualTo(500000);
                });
    }

    @Test
    void toolPropertiesBindCorrectly() {
        runner.withPropertyValues(
                        "kairo.model.api-key=test-key",
                        "kairo.tool.enable-file-tools=false",
                        "kairo.tool.enable-exec-tools=false",
                        "kairo.tool.enable-info-tools=false",
                        "kairo.tool.enable-agent-tools=true")
                .run(context -> {
                    AgentRuntimeProperties props = context.getBean(AgentRuntimeProperties.class);
                    assertThat(props.getTool().isEnableFileTools()).isFalse();
                    assertThat(props.getTool().isEnableExecTools()).isFalse();
                    assertThat(props.getTool().isEnableInfoTools()).isFalse();
                    assertThat(props.getTool().isEnableAgentTools()).isTrue();
                });
    }

    // ---- Conditional bean creation ----

    @Test
    void defaultAnthropicProviderCreatedWhenProviderNotSpecified() {
        runner.withPropertyValues("kairo.model.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(ModelProvider.class);
                    assertThat(context.getBean(ModelProvider.class))
                            .isInstanceOf(AnthropicProvider.class);
                });
    }

    @Test
    void defaultMemoryStoreIsInMemory() {
        runner.withPropertyValues("kairo.model.api-key=test-key")
                .run(context -> {
                    assertThat(context.getBean(MemoryStore.class))
                            .isInstanceOf(InMemoryStore.class);
                });
    }

    @Test
    void fileMemoryStoreCreatedWhenConfigured() {
        runner.withPropertyValues(
                        "kairo.model.api-key=test-key",
                        "kairo.memory.type=file",
                        "kairo.memory.file-store-path=/tmp/kairo-test-memory")
                .run(context -> {
                    assertThat(context.getBean(MemoryStore.class))
                            .isInstanceOf(FileMemoryStore.class);
                });
    }

    @Test
    void coreBeansAreCorrectImplementationTypes() {
        runner.withPropertyValues("kairo.model.api-key=test-key")
                .run(context -> {
                    assertThat(context.getBean(ToolRegistry.class))
                            .isInstanceOf(DefaultToolRegistry.class);
                    assertThat(context.getBean(PermissionGuard.class))
                            .isInstanceOf(DefaultPermissionGuard.class);
                    assertThat(context.getBean(ToolExecutor.class))
                            .isInstanceOf(DefaultToolExecutor.class);
                    assertThat(context.getBean(AgentFactory.class))
                            .isInstanceOf(DefaultAgentFactory.class);
                });
    }

    // ---- Default agent configuration ----

    @Test
    void defaultAgentUsesConfiguredProperties() {
        runner.withPropertyValues(
                        "kairo.model.api-key=test-key",
                        "kairo.agent.name=my-bot")
                .run(context -> {
                    Agent agent = context.getBean(Agent.class);
                    assertThat(agent.name()).isEqualTo("my-bot");
                });
    }

    // ---- Custom bean override ----

    @Test
    void userDefinedModelProviderTakesPrecedence() {
        runner.withUserConfiguration(CustomModelProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ModelProvider.class);
                    // The user-defined bean should be used, not the auto-configured one
                    assertThat(context.getBean(ModelProvider.class))
                            .isSameAs(CustomModelProviderConfig.CUSTOM_PROVIDER);
                });
    }

    @Test
    void actuatorAutoConfigurationNotActiveWithoutActuator() {
        runner.withPropertyValues("kairo.model.api-key=test-key")
                .run(context -> {
                    // AgentActuatorAutoConfiguration requires Endpoint class
                    // In this test, Actuator IS on the classpath (from starter-test),
                    // but AgentActuatorAutoConfiguration is not registered
                    assertThat(context)
                            .doesNotHaveBean(
                                    AgentActuatorAutoConfiguration.AgentEndpoint.class);
                });
    }

    // ---- Test configuration classes ----

    @Configuration(proxyBeanMethods = false)
    static class CustomModelProviderConfig {
        static final ModelProvider CUSTOM_PROVIDER =
                new io.kairo.core.model.AnthropicProvider("custom-key");

        @Bean
        ModelProvider modelProvider() {
            return CUSTOM_PROVIDER;
        }
    }
}
