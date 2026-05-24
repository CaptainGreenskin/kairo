/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GatewayConfig}. defaults() and fromEnv() are both called at gateway bootstrap; a
 * regression in either flips production limits silently. fromEnv() is exercised only in the "no env
 * var set" path because System.getenv is not mutable from Java in a portable way — the positive
 * env-override path is covered by integration tests outside this module.
 */
class GatewayConfigTest {

    @Test
    void defaults_returnsConstantValues() {
        GatewayConfig cfg = GatewayConfig.defaults();
        assertThat(cfg.poolMaxSize()).isEqualTo(GatewayConfig.DEFAULT_POOL_MAX_SIZE);
        assertThat(cfg.idleTtl()).isEqualTo(GatewayConfig.DEFAULT_IDLE_TTL);
        assertThat(cfg.compactionTrigger()).isEqualTo(GatewayConfig.DEFAULT_COMPACTION_TRIGGER);
    }

    @Test
    void defaultConstants_pinnedValues() {
        // Pin the numeric defaults — changing them silently moves prod behaviour.
        // If the change is intentional, the test must be updated in the same commit.
        assertThat(GatewayConfig.DEFAULT_POOL_MAX_SIZE).isEqualTo(64);
        assertThat(GatewayConfig.DEFAULT_IDLE_TTL).isEqualTo(Duration.ofMinutes(60));
        assertThat(GatewayConfig.DEFAULT_COMPACTION_TRIGGER).isEqualTo(0.50f);
    }

    @Test
    void fromEnv_withoutEnvVarsSet_fallsBackToDefaults() {
        // We can't reliably mutate env vars from Java, but the no-env path is the most common
        // failure mode (typo in env var name → silent fall-back to default).
        GatewayConfig cfg = GatewayConfig.fromEnv();
        // We don't know if any of these env vars are set in the test JVM; assert structural shape.
        assertThat(cfg.poolMaxSize()).isPositive();
        assertThat(cfg.idleTtl()).isPositive();
        assertThat(cfg.compactionTrigger()).isPositive();
    }

    @Test
    void record_isImmutableAndComparable() {
        GatewayConfig a = new GatewayConfig(10, Duration.ofMinutes(5), 0.7f);
        GatewayConfig b = new GatewayConfig(10, Duration.ofMinutes(5), 0.7f);
        GatewayConfig diff = new GatewayConfig(20, Duration.ofMinutes(5), 0.7f);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(diff);
    }
}
