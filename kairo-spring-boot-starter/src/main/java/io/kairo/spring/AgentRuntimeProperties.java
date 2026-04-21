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

    /**
     * Model provider configuration ({@code kairo.model.*}).
     *
     * <p>Configures which LLM provider to use, authentication credentials, and generation
     * parameters. Supports native Anthropic API and OpenAI-compatible providers (GLM, Qwen, GPT).
     */
    public static class Model {

        /**
         * Model provider type. Determines which API client is instantiated.
         *
         * <p>Valid values: {@code "anthropic"}, {@code "openai"} (also covers GLM, Qwen, GPT via
         * OpenAI-compatible endpoint).
         *
         * <p>Default: {@code "anthropic"}
         */
        private String provider = "anthropic";

        /**
         * API key for the model provider. If not set, the auto-configuration will attempt to read
         * from environment variables: {@code ANTHROPIC_API_KEY} (for Anthropic), {@code
         * OPENAI_API_KEY} / {@code GLM_API_KEY} / {@code QWEN_API_KEY} (for OpenAI-compatible
         * providers).
         *
         * <p>Default: {@code null} (falls back to environment variable)
         */
        private String apiKey;

        /**
         * Custom base URL for the provider API endpoint. Use this when connecting through a proxy
         * or when using an OpenAI-compatible provider (e.g. {@code
         * "https://open.bigmodel.cn/api/paas/v4"} for GLM).
         *
         * <p>Default: {@code null} (uses the provider's default endpoint)
         */
        private String baseUrl;

        /**
         * Model name to use for inference. Must be a model identifier recognized by the configured
         * provider.
         *
         * <p>Examples: {@code "claude-sonnet-4-20250514"}, {@code "glm-4-plus"}, {@code
         * "qwen-plus"}, {@code "gpt-4o"}
         *
         * <p>Default: {@value io.kairo.api.model.ModelConfig#DEFAULT_MODEL}
         */
        private String modelName = ModelConfig.DEFAULT_MODEL;

        /**
         * Maximum number of tokens the model may generate in a single response. Higher values allow
         * longer responses but increase latency and cost.
         *
         * <p>Valid range: 1–200,000 (provider-dependent upper bound)
         *
         * <p>Default: {@value io.kairo.api.model.ModelConfig#DEFAULT_MAX_TOKENS}
         */
        private int maxTokens = ModelConfig.DEFAULT_MAX_TOKENS;

        /**
         * Sampling temperature for generation. Lower values produce more deterministic output;
         * higher values produce more creative/random output.
         *
         * <p>Valid range: 0.0–2.0
         *
         * <p>Default: {@code 0.7}
         */
        private double temperature = 0.7;

        /**
         * Whether to enable extended thinking (Anthropic only). When enabled, the model performs
         * deeper reasoning before responding, which can improve quality on complex tasks at the
         * cost of additional tokens and latency.
         *
         * <p>Default: {@code false}
         */
        private boolean thinkingEnabled = false;

        /**
         * Budget tokens allocated for extended thinking (Anthropic only). Only used when {@link
         * #thinkingEnabled} is {@code true}. The model will use up to this many tokens for internal
         * reasoning before producing the visible response.
         *
         * <p>Valid range: 1,000–100,000
         *
         * <p>Default: {@code 10000}
         */
        private int thinkingBudget = 10000;

        /**
         * Circuit breaker configuration for model API calls ({@code
         * kairo.model.circuit-breaker.*}).
         */
        @NestedConfigurationProperty private CircuitBreaker circuitBreaker = new CircuitBreaker();

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

        /**
         * Circuit breaker configuration for model API calls ({@code
         * kairo.model.circuit-breaker.*}).
         *
         * <p>Implements a three-state circuit breaker (CLOSED → OPEN → HALF_OPEN) that prevents
         * cascading failures when the model API is unavailable. When open, calls fail fast without
         * contacting the provider.
         */
        public static class CircuitBreaker {

            /**
             * Whether the circuit breaker is enabled. When disabled, all model API calls proceed
             * without circuit-breaker protection.
             *
             * <p>Default: {@code true}
             */
            private boolean enabled = true;

            /**
             * Number of consecutive failures before the circuit transitions from CLOSED to OPEN.
             * Once open, subsequent calls fail immediately until {@link #resetTimeout} elapses.
             *
             * <p>Valid range: 1–100
             *
             * <p>Default: {@code 5}
             */
            private int failureThreshold = 5;

            /**
             * Duration to wait before transitioning from OPEN to HALF_OPEN. In the HALF_OPEN state,
             * a single probe request is allowed through; if it succeeds the circuit closes,
             * otherwise it re-opens.
             *
             * <p>Default: {@code 60s}
             */
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

    /**
     * Agent behavior configuration ({@code kairo.agent.*}).
     *
     * <p>Controls the ReAct loop parameters including iteration limits, timeouts, and token
     * budgets. These settings apply to the agent instance created by the Spring Boot
     * auto-configuration.
     */
    public static class Agent {

        /**
         * Agent name. Used as an identifier in logs, spans, and multi-agent orchestration.
         *
         * <p>Default: {@code "agent"}
         */
        private String name = "agent";

        /**
         * System prompt that defines the agent's persona and behavior. This prompt is prepended to
         * every conversation and guides the model's responses.
         *
         * <p>Default: {@code "You are a helpful coding assistant."}
         */
        private String systemPrompt = "You are a helpful coding assistant.";

        /**
         * Maximum number of ReAct loop iterations per invocation. Each iteration consists of one
         * Thought→Action→Observation cycle. The agent stops after this many iterations even if the
         * task is not complete.
         *
         * <p>Valid range: 1–1,000
         *
         * <p>Default: {@code 50}
         */
        private int maxIterations = 50;

        /**
         * Overall timeout for a single agent invocation, in seconds. If the agent does not complete
         * within this duration, it is cooperatively cancelled.
         *
         * <p>Valid range: 1–86,400 (1 second to 24 hours)
         *
         * <p>Default: {@code 1800} (30 minutes)
         */
        private int timeoutSeconds = 1800;

        /**
         * Token budget for the agent session. The total number of input + output tokens the agent
         * is allowed to consume before triggering context compaction or termination.
         *
         * <p>Valid range: 10,000–2,000,000
         *
         * <p>Default: {@code 200000}
         */
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

    /**
     * Tool registration configuration ({@code kairo.tool.*}).
     *
     * <p>Controls which tool categories are enabled and defines additional dangerous command
     * patterns for the permission guard. Tools are grouped into categories that can be
     * independently toggled.
     */
    public static class Tool {

        /**
         * Enable file operation tools (Read, Write, Edit, Glob, Grep). These tools allow the agent
         * to interact with the local filesystem.
         *
         * <p>Default: {@code true}
         */
        private boolean enableFileTools = true;

        /**
         * Enable execution tools (Bash, Monitor). These tools allow the agent to execute shell
         * commands. Use with caution in production; combine with {@link
         * AgentRuntimeProperties.Tool#dangerousPatterns} for safety.
         *
         * <p>Default: {@code true}
         */
        private boolean enableExecTools = true;

        /**
         * Enable information tools (AskUser). These tools allow the agent to prompt the user for
         * input during execution.
         *
         * <p>Default: {@code true}
         */
        private boolean enableInfoTools = true;

        /**
         * Enable agent collaboration tools (Spawn, SendMessage, Task*, Team*). These tools enable
         * multi-agent orchestration scenarios. Requires the {@code kairo-multi-agent} module on the
         * classpath.
         *
         * <p>Default: {@code false}
         */
        private boolean enableAgentTools = false;

        /**
         * Additional regex patterns for the permission guard to flag as dangerous. These are
         * appended to the built-in dangerous command patterns (e.g. {@code rm -rf}, {@code sudo}).
         *
         * <p>Example: {@code ["docker\\s+rm", "kubectl\\s+delete"]}
         *
         * <p>Default: empty list
         */
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

    /**
     * Skill loading configuration ({@code kairo.skills.*}).
     *
     * <p>Controls how Markdown-based skill definitions are discovered and loaded. Skills are
     * searched in the configured paths in order (lowest priority first).
     */
    public static class Skills {

        /**
         * Whether skill loading is enabled. When disabled, no skills are discovered or available to
         * the agent.
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
         * Whether skills are read-only (disallow create/edit/delete via skill management tools).
         * Set to {@code true} in production to prevent agents from modifying skill definitions.
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

    /**
     * Memory store configuration ({@code kairo.memory.*}).
     *
     * <p>Configures the backing store for agent memory (cross-session knowledge persistence). The
     * memory store is used by the Memory SPI to persist and retrieve knowledge entries.
     */
    public static class Memory {

        /**
         * Memory store type. Determines which storage backend is used.
         *
         * <p>Valid values: {@code "in-memory"} (non-persistent, lost on restart), {@code "file"}
         * (JSON files on disk), {@code "jdbc"} (relational database via JDBC).
         *
         * <p>Default: {@code "in-memory"}
         */
        private String type = "in-memory";

        /**
         * Store type alias (alternative to {@link #type}). Accepts the same values as {@code type}.
         * If set, takes precedence over {@code type}. Useful for Spring profiles where you want to
         * override the type without redefining all memory properties.
         *
         * <p>Default: {@code null} (defers to {@link #type})
         */
        private String storeType;

        /**
         * File store path for the {@code "file"} memory type. The directory where JSON memory files
         * are persisted.
         *
         * <p>Default: {@code null} (resolves to {@code ~/.kairo/memory})
         */
        private String fileStorePath;

        /**
         * Maximum number of entries to retain in the memory store. When exceeded, oldest entries
         * are evicted. Applies to all store types.
         *
         * <p>Valid range: 1–1,000,000
         *
         * <p>Default: {@code 10000}
         */
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

        /**
         * Resolve the effective store type. {@link #storeType} takes precedence over {@link #type}
         * when both are set.
         *
         * @return the resolved store type identifier
         */
        public String resolveStoreType() {
            return (storeType != null && !storeType.isBlank()) ? storeType : type;
        }
    }

    /**
     * Embedding provider configuration ({@code kairo.embedding.*}).
     *
     * <p>Configures the embedding provider used for semantic memory retrieval. When set to {@code
     * "noop"}, embedding-based retrieval is disabled and only keyword search is available.
     */
    public static class Embedding {

        /**
         * Embedding provider type. Determines which embedding backend is used for vectorizing
         * memory entries.
         *
         * <p>Valid values: {@code "noop"} (disabled — no embedding), or a custom provider
         * identifier registered via SPI.
         *
         * <p>Default: {@code "noop"}
         */
        private String provider = "noop";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    /**
     * Checkpoint configuration ({@code kairo.checkpoint.*}).
     *
     * <p>Controls agent state snapshot and restoration. When enabled, the agent's conversational
     * state can be serialized mid-conversation and restored later via {@code
     * AgentBuilder.restoreFrom(snapshot)}.
     */
    public static class Checkpoint {

        /**
         * Whether checkpoint management is enabled. When enabled, agents can create and restore
         * from state snapshots for durable execution and recovery scenarios.
         *
         * <p>Default: {@code false}
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
