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

import io.kairo.api.model.ModelConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Kairo, bound to the {@code kairo} prefix.
 *
 * <p>Example {@code application.yml}:
 *
 * <pre>{@code
 * kairo:
 *   model:
 *     provider: anthropic
 *     api-key: ${ANTHROPIC_API_KEY}
 *     model-name: claude-sonnet-4-20250514
 *   agent:
 *     name: my-agent
 *     system-prompt: You are a helpful assistant.
 *     max-iterations: 50
 *   tool:
 *     enable-file-tools: true
 *     enable-exec-tools: true
 *   skills:
 *     enabled: true
 *     search-paths:
 *       - classpath:skills
 *       - ./project-skills
 *       - ~/.kairo/skills
 *     readonly: false
 * }</pre>
 */
@ConfigurationProperties(prefix = "kairo")
public class AgentRuntimeProperties {

    /** Model/provider configuration under {@code kairo.model.*}. */
    @NestedConfigurationProperty private Model model = new Model();

    /** Runtime loop behavior under {@code kairo.agent.*}. */
    @NestedConfigurationProperty private Agent agent = new Agent();

    /** Tooling switches and safety patterns under {@code kairo.tool.*}. */
    @NestedConfigurationProperty private Tool tool = new Tool();

    /** Memory backend configuration under {@code kairo.memory.*}. */
    @NestedConfigurationProperty private Memory memory = new Memory();

    /** Skill loading/search configuration under {@code kairo.skills.*}. */
    @NestedConfigurationProperty private Skills skills = new Skills();

    /** Embedding provider selection under {@code kairo.embedding.*}. */
    @NestedConfigurationProperty private Embedding embedding = new Embedding();

    /** Agent checkpoint enablement under {@code kairo.checkpoint.*}. */
    @NestedConfigurationProperty private Checkpoint checkpoint = new Checkpoint();

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
    }

    public Memory getMemory() {
        return memory;
    }

    public void setMemory(Memory memory) {
        this.memory = memory;
    }

    public Skills getSkills() {
        return skills;
    }

    public void setSkills(Skills skills) {
        this.skills = skills;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
    }

    /** Model provider configuration. */
    public static class Model {
        /** Model provider type: "anthropic" or "openai". */
        private String provider = "anthropic";

        /** API key. Falls back to ANTHROPIC_API_KEY or OPENAI_API_KEY env vars. */
        private String apiKey;

        /** Custom base URL for API proxy. */
        private String baseUrl;

        /** Model name to use. */
        private String modelName = ModelConfig.DEFAULT_MODEL;

        /** Maximum tokens for model responses. */
        private int maxTokens = ModelConfig.DEFAULT_MAX_TOKENS;

        /** Temperature for generation. */
        private double temperature = 0.7;

        /** Whether to enable extended thinking (Anthropic only). */
        private boolean thinkingEnabled = false;

        /** Budget tokens for extended thinking. */
        private int thinkingBudget = 10000;

        /** Circuit breaker configuration. */
        private CircuitBreaker circuitBreaker = new CircuitBreaker();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public boolean isThinkingEnabled() {
            return thinkingEnabled;
        }

        public void setThinkingEnabled(boolean thinkingEnabled) {
            this.thinkingEnabled = thinkingEnabled;
        }

        public int getThinkingBudget() {
            return thinkingBudget;
        }

        public void setThinkingBudget(int thinkingBudget) {
            this.thinkingBudget = thinkingBudget;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        /** Circuit breaker configuration for model API calls. */
        public static class CircuitBreaker {
            /** Whether the circuit breaker is enabled. */
            private boolean enabled = true;

            /** Number of consecutive failures before the circuit opens. */
            private int failureThreshold = 5;

            /** Duration to wait before transitioning from OPEN to HALF_OPEN. */
            private Duration resetTimeout = Duration.ofSeconds(60);

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getFailureThreshold() {
                return failureThreshold;
            }

            public void setFailureThreshold(int failureThreshold) {
                this.failureThreshold = failureThreshold;
            }

            public Duration getResetTimeout() {
                return resetTimeout;
            }

            public void setResetTimeout(Duration resetTimeout) {
                this.resetTimeout = resetTimeout;
            }
        }
    }

    /** Agent behavior configuration. */
    public static class Agent {
        /** Agent name. */
        private String name = "agent";

        /** System prompt for the agent. */
        private String systemPrompt = "You are a helpful coding assistant.";

        /** Maximum ReAct loop iterations. */
        private int maxIterations = 50;

        /** Overall timeout in seconds. */
        private int timeoutSeconds = 1800;

        /** Token budget for the agent session. */
        private int tokenBudget = 200000;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getTokenBudget() {
            return tokenBudget;
        }

        public void setTokenBudget(int tokenBudget) {
            this.tokenBudget = tokenBudget;
        }
    }

    /** Tool registration configuration. */
    public static class Tool {
        /** Enable file operation tools (Read, Write, Edit, Glob, Grep). */
        private boolean enableFileTools = true;

        /** Enable execution tools (Bash, Monitor). */
        private boolean enableExecTools = true;

        /** Enable information tools (AskUser). */
        private boolean enableInfoTools = true;

        /** Enable agent collaboration tools (Spawn, SendMessage, Task*, Team*). */
        private boolean enableAgentTools = false;

        /** Additional dangerous patterns for the permission guard. */
        private List<String> dangerousPatterns = new ArrayList<>();

        public boolean isEnableFileTools() {
            return enableFileTools;
        }

        public void setEnableFileTools(boolean enableFileTools) {
            this.enableFileTools = enableFileTools;
        }

        public boolean isEnableExecTools() {
            return enableExecTools;
        }

        public void setEnableExecTools(boolean enableExecTools) {
            this.enableExecTools = enableExecTools;
        }

        public boolean isEnableInfoTools() {
            return enableInfoTools;
        }

        public void setEnableInfoTools(boolean enableInfoTools) {
            this.enableInfoTools = enableInfoTools;
        }

        public boolean isEnableAgentTools() {
            return enableAgentTools;
        }

        public void setEnableAgentTools(boolean enableAgentTools) {
            this.enableAgentTools = enableAgentTools;
        }

        public List<String> getDangerousPatterns() {
            return dangerousPatterns;
        }

        public void setDangerousPatterns(List<String> dangerousPatterns) {
            this.dangerousPatterns = dangerousPatterns;
        }
    }

    /** Skill loading configuration. */
    public static class Skills {
        /** Whether skill loading is enabled. */
        private boolean enabled = true;

        /**
         * Ordered search paths for skill files (lowest priority first). Supports:
         *
         * <ul>
         *   <li>{@code classpath:} prefix for classpath resources
         *   <li>{@code ~/} prefix for user home directory
         *   <li>plain filesystem paths
         * </ul>
         */
        private List<String> searchPaths = List.of("classpath:skills");

        /** Whether skills are read-only (disallow create/edit/delete). */
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

    /** Memory store configuration. */
    public static class Memory {
        /** Memory store type: "in-memory", "file", or "jdbc". */
        private String type = "in-memory";

        /**
         * Store type alias (alternative to 'type'). Supports "in-memory", "file", or "jdbc". If
         * set, takes precedence over 'type'.
         */
        private String storeType;

        /** File store path (for "file" type). Defaults to ~/.kairo/memory. */
        private String fileStorePath;

        /** Maximum number of entries to retain in the memory store. */
        private int maxEntries = 10000;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getStoreType() {
            return storeType;
        }

        public void setStoreType(String storeType) {
            this.storeType = storeType;
        }

        public String getFileStorePath() {
            return fileStorePath;
        }

        public void setFileStorePath(String fileStorePath) {
            this.fileStorePath = fileStorePath;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        /** Resolve the effective store type (storeType takes precedence over type). */
        public String resolveStoreType() {
            return (storeType != null && !storeType.isBlank()) ? storeType : type;
        }
    }

    /** Embedding provider configuration. */
    public static class Embedding {
        /** Embedding provider type: "noop" or custom. */
        private String provider = "noop";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    /** Checkpoint configuration. */
    public static class Checkpoint {
        /** Whether checkpoint management is enabled. */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
