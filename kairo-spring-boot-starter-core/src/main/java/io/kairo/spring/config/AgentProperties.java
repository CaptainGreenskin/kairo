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
package io.kairo.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent behavior configuration ({@code kairo.agent.*}).
 *
 * <p>Controls the ReAct loop parameters including iteration limits, timeouts, and token budgets.
 * These settings apply to the agent instance created by the Spring Boot auto-configuration.
 */
@ConfigurationProperties(prefix = "kairo.agent")
public class AgentProperties {

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
     * Thought→Action→Observation cycle. The agent stops after this many iterations even if the task
     * is not complete.
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
     * Token budget for the agent session. The total number of input + output tokens the agent is
     * allowed to consume before triggering context compaction or termination.
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
