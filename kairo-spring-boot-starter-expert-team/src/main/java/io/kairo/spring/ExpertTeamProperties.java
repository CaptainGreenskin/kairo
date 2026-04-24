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

import io.kairo.api.team.EvaluatorPreference;
import io.kairo.api.team.RiskProfile;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding for {@code kairo.expert-team.*} properties consumed by {@link
 * ExpertTeamAutoConfiguration}.
 *
 * <p>The auto-configuration is opt-in: nothing wires unless {@code kairo.expert-team.enabled=true}.
 * Default values mirror {@link io.kairo.api.team.TeamConfig#defaults()} so that callers can enable
 * the starter without tuning anything and still get a sane execution profile.
 *
 * @since v0.10
 */
@ConfigurationProperties(prefix = "kairo.expert-team")
public class ExpertTeamProperties {

    /** Master switch for the expert-team auto-configuration. */
    private boolean enabled = false;

    /** Default risk posture used when a request does not override it. */
    private RiskProfile defaultRiskProfile = RiskProfile.MEDIUM;

    /** Default maximum revise loops per step. */
    private int defaultMaxFeedbackRounds = 3;

    /** Default overall team timeout. */
    private Duration defaultTeamTimeout = Duration.ofMinutes(10L);

    /** Default evaluator preference; resolved against wired beans at request time. */
    private EvaluatorPreference defaultEvaluatorPreference = EvaluatorPreference.AUTO;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RiskProfile getDefaultRiskProfile() {
        return defaultRiskProfile;
    }

    public void setDefaultRiskProfile(RiskProfile defaultRiskProfile) {
        this.defaultRiskProfile = defaultRiskProfile;
    }

    public int getDefaultMaxFeedbackRounds() {
        return defaultMaxFeedbackRounds;
    }

    public void setDefaultMaxFeedbackRounds(int defaultMaxFeedbackRounds) {
        this.defaultMaxFeedbackRounds = defaultMaxFeedbackRounds;
    }

    public Duration getDefaultTeamTimeout() {
        return defaultTeamTimeout;
    }

    public void setDefaultTeamTimeout(Duration defaultTeamTimeout) {
        this.defaultTeamTimeout = defaultTeamTimeout;
    }

    public EvaluatorPreference getDefaultEvaluatorPreference() {
        return defaultEvaluatorPreference;
    }

    public void setDefaultEvaluatorPreference(EvaluatorPreference defaultEvaluatorPreference) {
        this.defaultEvaluatorPreference = defaultEvaluatorPreference;
    }
}
