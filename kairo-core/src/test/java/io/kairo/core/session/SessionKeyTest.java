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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SessionKey}. The record is small but `toString()` is the actual log/index key
 * for sessions — a regression there would scramble session lookups in observability dashboards.
 */
class SessionKeyTest {

    @Test
    void of_buildsRecordWithFields() {
        SessionKey k = SessionKey.of("ding", "user-123");
        assertThat(k.channelId()).isEqualTo("ding");
        assertThat(k.destination()).isEqualTo("user-123");
    }

    @Test
    void toString_formatsAsChannelColonDestination() {
        // Used as the log/metrics key — pinning the format because dashboards index on it.
        assertThat(SessionKey.of("slack", "C123").toString()).isEqualTo("slack:C123");
    }

    @Test
    void equalsAndHashCode_matchOnBothFields() {
        SessionKey a = SessionKey.of("x", "y");
        SessionKey b = SessionKey.of("x", "y");
        SessionKey diffChannel = SessionKey.of("z", "y");
        SessionKey diffDest = SessionKey.of("x", "w");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(diffChannel);
        assertThat(a).isNotEqualTo(diffDest);
    }

    @Test
    void constructor_rejectsNullChannelId() {
        assertThatThrownBy(() -> new SessionKey(null, "dest"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_rejectsNullDestination() {
        assertThatThrownBy(() -> new SessionKey("ch", null))
                .isInstanceOf(NullPointerException.class);
    }
}
