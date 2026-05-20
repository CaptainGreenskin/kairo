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
package io.kairo.spring.evolution;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the Kairo self-evolution subsystem, bound to {@code
 * kairo.evolution.*}.
 *
 * <p>Example {@code application.yml}:
 *
 * <pre>{@code
 * kairo:
 *   evolution:
 *     enabled: true
 *     skill-enabled: true
 *     memory-enabled: true
 *     iteration-threshold: 8
 *     review-model-name: claude-sonnet-4-20250514
 *     governance:
 *       quarantine: true
 *       scan-required: true
 * }</pre>
 *
 * @since v0.9 (Experimental)
 */
@ConfigurationProperties(prefix = "kairo.evolution")
@Validated
public class EvolutionProperties {

    /** Master switch for the evolution module. Default: false */
    private boolean enabled = false;

    /** Enable skill evolution. Default: false */
    private boolean skillEnabled = false;

    /** Enable memory evolution. Default: false */
    private boolean memoryEnabled = false;

    /** Auto-apply evolved skills without manual approval. Default: false */
    private boolean autoApply = false;

    /** Minimum iteration count before triggering evolution review. Default: 8 */
    @Min(1)
    private int iterationThreshold = 8;

    /** Model name for evolution review LLM calls. Null = use default model. */
    private String reviewModelName;

    /** Maximum consecutive failures before suspending evolution. Default: 3 */
    @Min(1)
    private int maxConsecutiveFailures = 3;

    /** Evolution review timeout in seconds. Default: 60 */
    @Min(5)
    private int reviewTimeoutSeconds = 60;

    private Governance governance = new Governance();

    /** Curator (skill telemetry + umbrella consolidation) settings. */
    private Curator curator = new Curator();

    public Curator getCurator() {
        return curator;
    }

    public void setCurator(Curator curator) {
        this.curator = curator;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSkillEnabled() {
        return skillEnabled;
    }

    public void setSkillEnabled(boolean skillEnabled) {
        this.skillEnabled = skillEnabled;
    }

    public boolean isMemoryEnabled() {
        return memoryEnabled;
    }

    public void setMemoryEnabled(boolean memoryEnabled) {
        this.memoryEnabled = memoryEnabled;
    }

    public boolean isAutoApply() {
        return autoApply;
    }

    public void setAutoApply(boolean autoApply) {
        this.autoApply = autoApply;
    }

    public int getIterationThreshold() {
        return iterationThreshold;
    }

    public void setIterationThreshold(int iterationThreshold) {
        this.iterationThreshold = iterationThreshold;
    }

    public String getReviewModelName() {
        return reviewModelName;
    }

    public void setReviewModelName(String reviewModelName) {
        this.reviewModelName = reviewModelName;
    }

    public int getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    public void setMaxConsecutiveFailures(int maxConsecutiveFailures) {
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public int getReviewTimeoutSeconds() {
        return reviewTimeoutSeconds;
    }

    public void setReviewTimeoutSeconds(int reviewTimeoutSeconds) {
        this.reviewTimeoutSeconds = reviewTimeoutSeconds;
    }

    public Governance getGovernance() {
        return governance;
    }

    public void setGovernance(Governance governance) {
        this.governance = governance;
    }

    public static class Governance {

        /** Require quarantine before activation. Default: true */
        private boolean quarantine = true;

        /** Require content scan before activation. Default: true */
        private boolean scanRequired = true;

        public boolean isQuarantine() {
            return quarantine;
        }

        public void setQuarantine(boolean quarantine) {
            this.quarantine = quarantine;
        }

        public boolean isScanRequired() {
            return scanRequired;
        }

        public void setScanRequired(boolean scanRequired) {
            this.scanRequired = scanRequired;
        }
    }

    /**
     * Configuration for the lifecycle curator + LLM-driven umbrella consolidation. Mirrors Hermes'
     * defaults: 7-day review interval, 2-hour idle gate, 30-day stale, 90-day archive.
     */
    public static class Curator {

        /** Master switch for the curator daemon. Default: false. */
        private boolean enabled = false;

        /** Auto-start the daemon at application startup. Default: false. */
        private boolean autoStart = false;

        /** Background review interval in minutes. Default: 10080 (7 days). */
        @Min(1)
        private long reviewIntervalMinutes = 60L * 24L * 7L;

        /**
         * Skip a review tick if the host has not been idle for at least this many minutes. Default:
         * 120 (2 hours). Set to 0 to disable the idle gate.
         */
        @Min(0)
        private long idleThresholdMinutes = 120L;

        /** Days an unused skill stays in ACTIVE before transitioning to STALE. Default: 30. */
        @Min(1)
        private int staleAfterDays = 30;

        /**
         * Days an unused skill stays in STALE before transitioning to ARCHIVED. Default: 90.
         * Archive is reversible — skills are not deleted.
         */
        @Min(1)
        private int archiveAfterDays = 90;

        /**
         * Filesystem directory for the file-backed {@code SkillTelemetryStore}. Defaults to {@code
         * ~/.kairo/evolution/}.
         */
        private String telemetryDirectory;

        /**
         * Filesystem root for {@link io.kairo.evolution.curator.CuratorAction.DemoteToSupport}
         * actions ({@code references/}, {@code templates/}, {@code scripts/}). Defaults to the
         * skill store directory if available.
         */
        private String supportDirectory;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoStart() {
            return autoStart;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }

        public long getReviewIntervalMinutes() {
            return reviewIntervalMinutes;
        }

        public void setReviewIntervalMinutes(long reviewIntervalMinutes) {
            this.reviewIntervalMinutes = reviewIntervalMinutes;
        }

        public long getIdleThresholdMinutes() {
            return idleThresholdMinutes;
        }

        public void setIdleThresholdMinutes(long idleThresholdMinutes) {
            this.idleThresholdMinutes = idleThresholdMinutes;
        }

        public int getStaleAfterDays() {
            return staleAfterDays;
        }

        public void setStaleAfterDays(int staleAfterDays) {
            this.staleAfterDays = staleAfterDays;
        }

        public int getArchiveAfterDays() {
            return archiveAfterDays;
        }

        public void setArchiveAfterDays(int archiveAfterDays) {
            this.archiveAfterDays = archiveAfterDays;
        }

        public String getTelemetryDirectory() {
            return telemetryDirectory;
        }

        public void setTelemetryDirectory(String telemetryDirectory) {
            this.telemetryDirectory = telemetryDirectory;
        }

        public String getSupportDirectory() {
            return supportDirectory;
        }

        public void setSupportDirectory(String supportDirectory) {
            this.supportDirectory = supportDirectory;
        }
    }
}
