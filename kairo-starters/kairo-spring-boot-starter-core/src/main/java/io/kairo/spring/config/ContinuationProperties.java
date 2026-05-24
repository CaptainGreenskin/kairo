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
 * Continuation strategy configuration ({@code kairo.agent.continuation.*}).
 *
 * <p>Controls whether the agent loop continuation framework is active and its tuning parameters.
 * When disabled (the default), the agent uses {@link
 * io.kairo.core.agent.continuation.NoopContinuationStrategy} which preserves pre-0.5.0 behavior.
 *
 * <p>When enabled, a {@link io.kairo.core.agent.continuation.CompositeContinuationStrategy} is
 * configured with:
 *
 * <ul>
 *   <li>{@link io.kairo.core.agent.continuation.FinishReasonRecoveryStrategy} — recovers from
 *       max-token cutoffs
 *   <li>{@link io.kairo.core.agent.continuation.PendingTodoNudgeStrategy} — nudges when tasks
 *       remain
 *   <li>{@link io.kairo.core.agent.continuation.RecentToolActivityStrategy} — nudges when model was
 *       recently active
 * </ul>
 *
 * @since 0.5.0
 */
@ConfigurationProperties(prefix = "kairo.agent.continuation")
public class ContinuationProperties {

    /**
     * Whether to enable the continuation strategy framework.
     *
     * <p>When {@code true}, the composite continuation strategy is registered and wired into the
     * agent builder. When {@code false} (default), the noop strategy preserves legacy termination
     * behavior.
     *
     * <p>Default: {@code false}
     */
    private boolean enabled = false;

    /**
     * Maximum number of length-recovery retries for {@link
     * io.kairo.core.agent.continuation.FinishReasonRecoveryStrategy}.
     *
     * <p>Controls how many times the strategy will inject a "please continue" prompt when the model
     * response is cut off due to max output tokens.
     *
     * <p>Valid range: 1–10
     *
     * <p>Default: {@code 3}
     */
    private int maxLengthRetries = 3;

    /**
     * Number of recent iterations to check for tool activity in {@link
     * io.kairo.core.agent.continuation.RecentToolActivityStrategy}.
     *
     * <p>If tool calls occurred within this lookback window, the model is considered mid-task.
     *
     * <p>Valid range: 1–10
     *
     * <p>Default: {@code 3}
     */
    private int toolActivityLookback = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxLengthRetries() {
        return maxLengthRetries;
    }

    public void setMaxLengthRetries(int maxLengthRetries) {
        this.maxLengthRetries = maxLengthRetries;
    }

    public int getToolActivityLookback() {
        return toolActivityLookback;
    }

    public void setToolActivityLookback(int toolActivityLookback) {
        this.toolActivityLookback = toolActivityLookback;
    }
}
