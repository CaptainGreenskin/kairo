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
package io.kairo.core.context;

/**
 * Externalized compaction thresholds — every value has a sensible default (Principle #6).
 *
 * <p>Users may override any subset; unspecified values keep the current hardcoded defaults that
 * have been proven in production.
 *
 * @param triggerPressure overall pressure threshold that triggers compaction (default 0.80)
 * @param snipPressure snip-compaction stage trigger (default 0.80)
 * @param microPressure micro-compaction stage trigger (default 0.85)
 * @param collapsePressure collapse-compaction stage trigger (default 0.90)
 * @param autoPressure auto (LLM summary) compaction trigger (default 0.95)
 * @param partialPressure last-resort partial compaction trigger (default 0.98)
 * @param cbFailureLimit circuit-breaker consecutive failure limit (default 3)
 * @param cbCooldownSeconds circuit-breaker cooldown in seconds (default 30)
 * @param bufferTokens overhead buffer reserved beyond model output tokens (default 13000)
 */
public record CompactionThresholds(
        float triggerPressure,
        float snipPressure,
        float microPressure,
        float collapsePressure,
        float autoPressure,
        float partialPressure,
        int cbFailureLimit,
        long cbCooldownSeconds,
        int bufferTokens) {

    /** Default trigger pressure for overall compaction gating. */
    public static final float DEFAULT_TRIGGER_PRESSURE = 0.80f;

    /** Default snip-compaction stage trigger. */
    public static final float DEFAULT_SNIP_PRESSURE = 0.80f;

    /** Default micro-compaction stage trigger. */
    public static final float DEFAULT_MICRO_PRESSURE = 0.85f;

    /** Default collapse-compaction stage trigger. */
    public static final float DEFAULT_COLLAPSE_PRESSURE = 0.90f;

    /** Default auto (LLM summary) compaction trigger. */
    public static final float DEFAULT_AUTO_PRESSURE = 0.95f;

    /** Default partial compaction trigger. */
    public static final float DEFAULT_PARTIAL_PRESSURE = 0.98f;

    /** Default circuit-breaker consecutive failure limit. */
    public static final int DEFAULT_CB_FAILURE_LIMIT = 3;

    /** Default circuit-breaker cooldown in seconds. */
    public static final long DEFAULT_CB_COOLDOWN_SECONDS = 30;

    /** Default overhead buffer in tokens. */
    public static final int DEFAULT_BUFFER_TOKENS = 13_000;

    /**
     * Sensible-defaults instance. Behaviour is identical to pre-externalization hardcoded values.
     */
    public static final CompactionThresholds DEFAULTS = new CompactionThresholds();

    /** Create thresholds with all sensible defaults. */
    public CompactionThresholds() {
        this(
                DEFAULT_TRIGGER_PRESSURE,
                DEFAULT_SNIP_PRESSURE,
                DEFAULT_MICRO_PRESSURE,
                DEFAULT_COLLAPSE_PRESSURE,
                DEFAULT_AUTO_PRESSURE,
                DEFAULT_PARTIAL_PRESSURE,
                DEFAULT_CB_FAILURE_LIMIT,
                DEFAULT_CB_COOLDOWN_SECONDS,
                DEFAULT_BUFFER_TOKENS);
    }

    /** Canonical constructor with validation. */
    public CompactionThresholds {
        if (triggerPressure <= 0.0f || triggerPressure > 1.0f) {
            triggerPressure = DEFAULT_TRIGGER_PRESSURE;
        }
        if (snipPressure <= 0.0f || snipPressure > 1.0f) {
            snipPressure = DEFAULT_SNIP_PRESSURE;
        }
        if (microPressure <= 0.0f || microPressure > 1.0f) {
            microPressure = DEFAULT_MICRO_PRESSURE;
        }
        if (collapsePressure <= 0.0f || collapsePressure > 1.0f) {
            collapsePressure = DEFAULT_COLLAPSE_PRESSURE;
        }
        if (autoPressure <= 0.0f || autoPressure > 1.0f) {
            autoPressure = DEFAULT_AUTO_PRESSURE;
        }
        if (partialPressure <= 0.0f || partialPressure > 1.0f) {
            partialPressure = DEFAULT_PARTIAL_PRESSURE;
        }
        if (cbFailureLimit <= 0) {
            cbFailureLimit = DEFAULT_CB_FAILURE_LIMIT;
        }
        if (cbCooldownSeconds < 0) {
            cbCooldownSeconds = DEFAULT_CB_COOLDOWN_SECONDS;
        }
        if (bufferTokens < 0) {
            bufferTokens = DEFAULT_BUFFER_TOKENS;
        }
    }

    /**
     * Fluent builder for partially overriding defaults.
     *
     * @return a new builder pre-populated with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder that starts from sensible defaults. */
    public static class Builder {
        private float triggerPressure = DEFAULT_TRIGGER_PRESSURE;
        private float snipPressure = DEFAULT_SNIP_PRESSURE;
        private float microPressure = DEFAULT_MICRO_PRESSURE;
        private float collapsePressure = DEFAULT_COLLAPSE_PRESSURE;
        private float autoPressure = DEFAULT_AUTO_PRESSURE;
        private float partialPressure = DEFAULT_PARTIAL_PRESSURE;
        private int cbFailureLimit = DEFAULT_CB_FAILURE_LIMIT;
        private long cbCooldownSeconds = DEFAULT_CB_COOLDOWN_SECONDS;
        private int bufferTokens = DEFAULT_BUFFER_TOKENS;

        private Builder() {}

        public Builder triggerPressure(float v) {
            this.triggerPressure = v;
            return this;
        }

        public Builder snipPressure(float v) {
            this.snipPressure = v;
            return this;
        }

        public Builder microPressure(float v) {
            this.microPressure = v;
            return this;
        }

        public Builder collapsePressure(float v) {
            this.collapsePressure = v;
            return this;
        }

        public Builder autoPressure(float v) {
            this.autoPressure = v;
            return this;
        }

        public Builder partialPressure(float v) {
            this.partialPressure = v;
            return this;
        }

        public Builder cbFailureLimit(int v) {
            this.cbFailureLimit = v;
            return this;
        }

        public Builder cbCooldownSeconds(long v) {
            this.cbCooldownSeconds = v;
            return this;
        }

        public Builder bufferTokens(int v) {
            this.bufferTokens = v;
            return this;
        }

        public CompactionThresholds build() {
            return new CompactionThresholds(
                    triggerPressure,
                    snipPressure,
                    microPressure,
                    collapsePressure,
                    autoPressure,
                    partialPressure,
                    cbFailureLimit,
                    cbCooldownSeconds,
                    bufferTokens);
        }
    }
}
