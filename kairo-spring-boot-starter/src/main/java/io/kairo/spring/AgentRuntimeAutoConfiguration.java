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
import io.kairo.api.agent.AgentFactory;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.tool.PermissionGuard;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.agent.DefaultAgentFactory;
import io.kairo.core.memory.FileMemoryStore;
import io.kairo.core.memory.InMemoryStore;
import io.kairo.core.model.AnthropicProvider;
import io.kairo.core.model.ModelCircuitBreaker;
import io.kairo.core.model.OpenAIProvider;
import io.kairo.core.shutdown.GracefulShutdownManager;
import io.kairo.core.skill.DefaultSkillRegistry;
import io.kairo.core.skill.SkillLoader;
import io.kairo.core.tool.AnnotationToolScanner;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.tools.skill.SkillListTool;
import io.kairo.tools.skill.SkillLoadTool;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for Kairo.
 *
 * <p>Provides sensible defaults for all runtime components while allowing full customization
 * through {@link AgentRuntimeProperties} or by defining your own beans (all beans are
 * {@code @ConditionalOnMissingBean}).
 */
@AutoConfiguration
@EnableConfigurationProperties(AgentRuntimeProperties.class)
@ConditionalOnClass(Agent.class)
public class AgentRuntimeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeAutoConfiguration.class);

    // ---- Model Provider ----

    @Bean
    @ConditionalOnMissingBean(ModelProvider.class)
    @ConditionalOnProperty(
            name = "kairo.model.provider",
            havingValue = "anthropic",
            matchIfMissing = true)
    public ModelProvider anthropicModelProvider(AgentRuntimeProperties properties) {
        AgentRuntimeProperties.Model model = properties.getModel();
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
    public ModelProvider openaiModelProvider(AgentRuntimeProperties properties) {
        AgentRuntimeProperties.Model model = properties.getModel();
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
    public ToolRegistry toolRegistry(AgentRuntimeProperties properties) {
        DefaultToolRegistry registry = new DefaultToolRegistry();
        AgentRuntimeProperties.Tool toolProps = properties.getTool();

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
    public PermissionGuard permissionGuard(AgentRuntimeProperties properties) {
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        for (String pattern : properties.getTool().getDangerousPatterns()) {
            guard.addDangerousPattern(pattern);
        }
        return guard;
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutor toolExecutor(
            DefaultToolRegistry toolRegistry,
            PermissionGuard permissionGuard,
            GracefulShutdownManager gracefulShutdownManager) {
        return new DefaultToolExecutor(
                toolRegistry, permissionGuard, null, gracefulShutdownManager);
    }

    // ---- Agent Factory ----

    @Bean
    @ConditionalOnMissingBean
    public AgentFactory agentFactory(
            ToolExecutor toolExecutor, GracefulShutdownManager gracefulShutdownManager) {
        return new DefaultAgentFactory(toolExecutor, gracefulShutdownManager);
    }

    // ---- Memory Store ----

    @Bean
    @ConditionalOnMissingBean
    public MemoryStore memoryStore(AgentRuntimeProperties properties) {
        return switch (properties.getMemory().getType()) {
            case "file" -> {
                String path = properties.getMemory().getFileStorePath();
                if (path == null || path.isBlank()) {
                    path = System.getProperty("user.home") + "/.kairo/memory";
                }
                log.info("Using file-based memory store at: {}", path);
                yield new FileMemoryStore(Path.of(path));
            }
            default -> {
                log.info("Using in-memory store");
                yield new InMemoryStore();
            }
        };
    }

    // ---- Skill Infrastructure ----

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "kairo.skills.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public SkillRegistry skillRegistry() {
        return new DefaultSkillRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "kairo.skills.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public SkillLoader skillLoader(SkillRegistry skillRegistry) {
        return new SkillLoader(skillRegistry);
    }

    @Bean
    @ConditionalOnProperty(
            name = "kairo.skills.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public SkillListTool skillListTool(
            SkillRegistry skillRegistry, DefaultToolRegistry toolRegistry) {
        SkillListTool tool = new SkillListTool(skillRegistry);
        var scanner = new AnnotationToolScanner();
        toolRegistry.register(scanner.scanClass(SkillListTool.class));
        toolRegistry.registerInstance("skill_list", tool);
        log.info("Registered skill_list tool");
        return tool;
    }

    @Bean
    @ConditionalOnProperty(
            name = "kairo.skills.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public SkillLoadTool skillLoadTool(
            SkillRegistry skillRegistry,
            SkillLoader skillLoader,
            DefaultToolRegistry toolRegistry) {
        SkillLoadTool tool = new SkillLoadTool(skillRegistry, skillLoader);
        var scanner = new AnnotationToolScanner();
        toolRegistry.register(scanner.scanClass(SkillLoadTool.class));
        toolRegistry.registerInstance("skill_load", tool);
        log.info("Registered skill_load tool");
        return tool;
    }

    // ---- Default Agent ----

    @Bean
    @ConditionalOnMissingBean(Agent.class)
    public Agent defaultAgent(
            ModelProvider modelProvider,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            GracefulShutdownManager gracefulShutdownManager,
            AgentRuntimeProperties properties) {

        AgentRuntimeProperties.Agent agentProps = properties.getAgent();

        Agent agent =
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
                        .build();

        log.info(
                "Created default agent '{}' (maxIterations={}, tokenBudget={})",
                agentProps.getName(),
                agentProps.getMaxIterations(),
                agentProps.getTokenBudget());
        return agent;
    }

    // ---- Helpers ----

    private static String resolveApiKey(String configured, String envVarName) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return System.getenv(envVarName);
    }

    // ---- Circuit Breaker ----

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "kairo.model.circuit-breaker.enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ModelCircuitBreaker modelCircuitBreaker(AgentRuntimeProperties properties) {
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
    public GracefulShutdownManager gracefulShutdownManager(AgentRuntimeProperties properties) {
        GracefulShutdownManager manager = new GracefulShutdownManager();
        int timeoutSeconds = properties.getAgent().getTimeoutSeconds();
        manager.setShutdownTimeout(Duration.ofSeconds(Math.min(timeoutSeconds, 30)));
        log.info("Configured graceful shutdown manager");
        return manager;
    }
}
