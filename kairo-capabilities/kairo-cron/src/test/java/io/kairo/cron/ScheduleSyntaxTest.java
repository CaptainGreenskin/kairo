/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ScheduleSyntaxTest {

    @Test
    void cronExpressionPassesThrough() {
        assertThat(ScheduleSyntax.toCron("0 9 * * *")).isEqualTo("0 9 * * *");
        assertThat(ScheduleSyntax.toCron("*/15 * * * *")).isEqualTo("*/15 * * * *");
    }

    @Test
    void intervalMinutesTranslatesToStarSlash() {
        assertThat(ScheduleSyntax.toCron("every 5m")).isEqualTo("*/5 * * * *");
        assertThat(ScheduleSyntax.toCron("every 30m")).isEqualTo("*/30 * * * *");
    }

    @Test
    void intervalHoursTranslates() {
        assertThat(ScheduleSyntax.toCron("every 2h")).isEqualTo("0 */2 * * *");
        assertThat(ScheduleSyntax.toCron("every 6h")).isEqualTo("0 */6 * * *");
    }

    @Test
    void intervalDaysTranslates() {
        assertThat(ScheduleSyntax.toCron("every 1d")).isEqualTo("0 0 */1 * *");
        assertThat(ScheduleSyntax.toCron("every 7d")).isEqualTo("0 0 */7 * *");
    }

    @Test
    void everyOneDayAtTimeAnchorsTime() {
        assertThat(ScheduleSyntax.toCron("every 1d at 09:00")).isEqualTo("00 9 * * *");
        assertThat(ScheduleSyntax.toCron("every 1d at 23:59")).isEqualTo("59 23 * * *");
    }

    @Test
    void caseInsensitive() {
        assertThat(ScheduleSyntax.toCron("EVERY 5M")).isEqualTo("*/5 * * * *");
    }

    @Test
    void subMinuteIntervalRejected() {
        assertThatThrownBy(() -> ScheduleSyntax.toCron("every 30s"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sub-minute");
    }

    @Test
    void invalidTimeOfDayRejected() {
        assertThatThrownBy(() -> ScheduleSyntax.toCron("every 1d at 25:00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleSyntax.toCron("every 1d at 09:60"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void garbledInputRejected() {
        assertThatThrownBy(() -> ScheduleSyntax.toCron("sometime tomorrow"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognised");
    }

    @Test
    void multiDayWithTimeAnchorRejected() {
        assertThatThrownBy(() -> ScheduleSyntax.toCron("every 3d at 09:00"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
