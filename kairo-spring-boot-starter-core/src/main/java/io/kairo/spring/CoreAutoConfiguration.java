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
package io.kairo.spring;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentBuilderCustomizer;
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.agent.SystemPromptContributor;
import io.kairo.api.execution.DurableExecutionStore;
import io.kairo.api.guardrail.GuardrailChain;
import io.kairo.api.guardrail.GuardrailPolicy;
import io.kairo.api.guardrail.SecurityEventSink;
import io.kairo.api.middleware.Middleware;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.routing.RoutingPolicy;
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.agent.DefaultAgentFactory;
import io.kairo.core.execution.RecoveryHandler;
import io.kairo.core.guardrail.DefaultGuardrailChain;
import io.kairo.core.guardrail.LoggingSecurityEventSink;
import io.kairo.core.model.ModelCircuitBreaker;
import io.kairo.core.model.anthropic.AnthropicProvider;
import io.kairo.core.model.openai.OpenAIProvider;
import io.kairo.core.routing.DefaultRoutingPolicy;
import io.kairo.core.shutdown.GracefulShutdownManager;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.spring.config.AgentProperties;
import io.kairo.spring.config.ModelProperties;
import io.kairo.spring.config.ToolProperties;
import io.kairo.spring.execution.DurableExecutionProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core auto-configuration: model providers, tool infrastructure, agent factory, circuit breaker,
 * graceful shutdown, and the default agent bean.
 *
 * <p>Imported by {@link AgentRuntimeAutoConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
class CoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CoreAutoConfiguration.class);

    // ---- Model Provider ----

    @Bean
    @ConditionalOnMissingBean(ModelProvider.class)
    @ConditionalOnProperty(
            name = "kairo.model.provider",
            havingValue = "anthropic",
            matchIfMissing = true)
    ModelProvider anthropicModelProvider(AgentRuntimeProperties properties) {
        ModelProperties model = properties.getModel();
        String apiKey = resolveApiKey(model.getApiKey(), "ANTHROPIC_API_KEY");
        if (apiKey == null) {
            throw new IllegalStateException(
                    "Anthropic API key not configured. Set 'kairo.model.api-key' or ANTHROPIC_API_KEY env var");
        }
        String baseUrl = model.getBaseUrl();
        AnthropicProvider provider =
                (baseUrl != null && !baseUrl.isBlank())
                        ? new AnthropicProvider(apiKey, baseUrl)
                        : new AnthropicProvider(apiKey);
        log.info("Configured Anthropic model provider (model={})", model.getModelName());
        return provider;
    }

    @Bean
    @ConditionalOnMissingBean(ModelProvider.class)
    @ConditionalOnProperty(name = "kairo.model.provider", havingValue = "openai")
    ModelProvider openaiModelProvider(AgentRuntimeProperties properties) {
        ModelProperties model = properties.getModel();
        String apiKey = resolveApiKey(model.getApiKey(), "OPENAI_API_KEY");
        if (apiKey == null) {
            throw new IllegalStateException(
                    "OpenAI API key not configured. Set 'kairo.model.api-key' or OPENAI_API_KEY env var");
        }
        String baseUrl = model.getBaseUrl();
        OpenAIProvider provider =
                (baseUrl != null && !baseUrl.isBlank())
                        ? new OpenAIProvider(apiKey, baseUrl)
                        : new OpenAIProvider(apiKey);
        log.info("Configured OpenAI model provider (model={})", model.getModelName());
        return provider;
    }

    // ---- Tool Infrastructure ----

    @Bean
    @ConditionalOnMissingBean
    ToolRegistry toolRegistry(AgentRuntimeProperties properties) {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        ToolProperties toolProps = properties.getTool();

        if (toolProps.isEnableFileTools()) {
            registry.scan("io.kairo.tools.file");
            log.info("Registered file tools");
        }
        if (toolProps.isEnableExecTools()) {
            registry.scan("io.kairo.tools.exec");
            log.info("Registered exec tools");
        }
        if (toolProps.isEnableInfoTools()) {
            registry.scan("io.kairo.tools.info");
            log.info("Registered info tools");
        }
        if (toolProps.isEnableAgentTools()) {
            registry.scan("io.kairo.tools.agent");
            log.info("Registered agent collaboration tools");
        }

        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    PermissionGuard permissionGuard(AgentRuntimeProperties properties) {
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        for (String pattern : properties.getTool().getDangerousPatterns()) {
            guard.addDangerousPattern(pattern);
        }
        return guard;
    }

    /**
     * Default {@link SecurityEventSink} bean that logs security events via SLF4J.
     *
     * <p>Uses {@link LoggingSecurityEventSink}: DENY and MCP_BLOCK events are logged at WARN level,
     * all others at INFO level. Replace this bean with a custom {@link SecurityEventSink}
     * implementation to route security events to an external audit system (e.g., SIEM, database).
     */
    @Bean
    @ConditionalOnMissingBean
    SecurityEventSink securityEventSink() {
        return new LoggingSecurityEventSink();
    }

    /**
     * Default {@link GuardrailChain} bean that evaluates all registered {@link GuardrailPolicy}
     * instances in order, emitting {@link io.kairo.api.guardrail.SecurityEvent}s to the configured
     * {@link SecurityEventSink}.
     *
     * <p>Collects all {@link GuardrailPolicy} beans from the application context (if any) and wires
     * them into a {@link DefaultGuardrailChain}. Replace this bean to provide a custom chain
     * implementation with different ordering or short-circuit semantics.
     */
    @Bean
    @ConditionalOnMissingBean
    GuardrailChain guardrailChain(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    List<GuardrailPolicy> policies,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    SecurityEventSink securityEventSink) {
        return new DefaultGuardrailChain(
                policies != null ? policies : List.of(), securityEventSink);
    }

    /**
     * Default {@link RoutingPolicy} bean that uses {@link DefaultRoutingPolicy}.
     *
     * <p>The default policy applies simple model routing without cost awareness or provider
     * preferences. Replace this bean with a custom {@link RoutingPolicy} implementation to add
     * cost-based routing, latency-aware selection, or provider failover strategies.
     */
    @Bean
    @ConditionalOnMissingBean
    RoutingPolicy routingPolicy() {
        return new DefaultRoutingPolicy();
    }

    @Bean
    @ConditionalOnMissingBean
    ToolExecutor toolExecutor(
            DefaultToolRegistry toolRegistry,
            PermissionGuard permissionGuard,
            GracefulShutdownManager gracefulShutdownManager,
            GuardrailChain guardrailChain) {
        return new DefaultToolExecutor(
                toolRegistry, permissionGuard, null, gracefulShutdownManager, 3, guardrailChain);
    }

    // ---- Agent Factory ----

    @Bean
    @ConditionalOnMissingBean
    AgentFactory agentFactory(
            ToolExecutor toolExecutor,
            GracefulShutdownManager gracefulShutdownManager,
            GuardrailChain guardrailChain) {
        return new DefaultAgentFactory(toolExecutor, gracefulShutdownManager, guardrailChain);
    }

    // ---- Default Agent ----

    @Bean
    @ConditionalOnMissingBean(Agent.class)
    Agent defaultAgent(
            ModelProvider modelProvider,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            GracefulShutdownManager gracefulShutdownManager,
            GuardrailChain guardrailChain,
            AgentRuntimeProperties properties,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    List<Middleware> middlewares,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    DurableExecutionStore durableExecutionStore,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    RecoveryHandler recoveryHandler,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    DurableExecutionProperties durableProperties,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    List<AgentBuilderCustomizer> customizers,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    List<SystemPromptContributor> systemPromptContributors) {

        AgentProperties agentProps = properties.getAgent();

        AgentBuilder builder =
                AgentBuilder.create()
                        .name(agentProps.getName())
                        .model(modelProvider)
                        .tools(toolRegistry)
                        .toolExecutor(toolExecutor)
                        .modelName(properties.getModel().getModelName())
                        .systemPrompt(agentProps.getSystemPrompt())
                        .maxIterations(agentProps.getMaxIterations())
                        .timeout(Duration.ofSeconds(agentProps.getTimeoutSeconds()))
                        .tokenBudget(agentProps.getTokenBudget())
                        .shutdownManager(gracefulShutdownManager)
                        .guardrailChain(guardrailChain);

        if (middlewares != null) {
            for (Middleware mw : middlewares) {
                builder.middleware(mw);
            }
        }

        // Wire durable execution if enabled
        if (durableProperties != null
                && durableProperties.isEnabled()
                && durableExecutionStore != null
                && recoveryHandler != null) {
            builder.durableExecutionStore(durableExecutionStore)
                    .recoveryHandler(recoveryHandler)
                    .recoveryOnStartup(durableProperties.isRecoveryOnStartup());
        }

        // Apply registered customizers (e.g., evolution hook wiring)
        if (customizers != null) {
            customizers.forEach(c -> c.customize(builder));
        }

        // Apply registered system prompt contributors
        if (systemPromptContributors != null) {
            systemPromptContributors.forEach(builder::systemPromptContributor);
        }

        Agent agent = builder.build();

        log.info(
                "Created default agent '{}' (maxIterations={}, tokenBudget={})",
                agentProps.getName(),
                agentProps.getMaxIterations(),
                agentProps.getTokenBudget());
        return agent;
    }

    // ---- Circuit Breaker ----

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "kairo.model.circuit-breaker.enabled",
            havingValue = "true",
            matchIfMissing = true)
    ModelCircuitBreaker modelCircuitBreaker(AgentRuntimeProperties properties) {
        var cbProps = properties.getModel().getCircuitBreaker();
        ModelCircuitBreaker breaker =
                new ModelCircuitBreaker(
                        properties.getModel().getModelName(),
                        cbProps.getFailureThreshold(),
                        cbProps.getResetTimeout());
        log.info(
                "Configured model circuit breaker (threshold={}, resetTimeout={})",
                cbProps.getFailureThreshold(),
                cbProps.getResetTimeout());
        return breaker;
    }

    // ---- Graceful Shutdown ----

    @Bean
    @ConditionalOnMissingBean
    GracefulShutdownManager gracefulShutdownManager(AgentRuntimeProperties properties) {
        GracefulShutdownManager manager = new GracefulShutdownManager();
        int timeoutSeconds = properties.getAgent().getTimeoutSeconds();
        manager.setShutdownTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 30)));
        log.info("Configured graceful shutdown manager");
        return manager;
    }

    // ---- Helpers ----

    private static String resolveApiKey(String configured, String envVarName) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return System.getenv(envVarName);
    }
}
