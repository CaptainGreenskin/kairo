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
package io.kairo.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class CronExpressionTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void everyMinute() {
        CronExpression expr = CronExpression.parse("* * * * *");
        ZonedDateTime time = ZonedDateTime.of(2026, 5, 15, 10, 30, 0, 0, UTC);
        assertThat(expr.matches(time)).isTrue();
    }

    @Test
    void everyFiveMinutes() {
        CronExpression expr = CronExpression.parse("*/5 * * * *");
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 15, 10, 0, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 15, 10, 5, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 15, 10, 3, 0, 0, UTC))).isFalse();
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 15, 10, 15, 0, 0, UTC))).isTrue();
    }

    @Test
    void weekdaysAt9am() {
        CronExpression expr = CronExpression.parse("0 9 * * 1-5");
        // 2026-05-15 is Friday (dow=5)
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 15, 9, 0, 0, 0, UTC))).isTrue();
        // 2026-05-17 is Sunday (dow=0)
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 17, 9, 0, 0, 0, UTC))).isFalse();
        // Wrong hour
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 15, 10, 0, 0, 0, UTC))).isFalse();
    }

    @Test
    void specificDayOfMonth() {
        CronExpression expr = CronExpression.parse("30 14 28 2 *");
        assertThat(expr.matches(ZonedDateTime.of(2026, 2, 28, 14, 30, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 2, 27, 14, 30, 0, 0, UTC))).isFalse();
        assertThat(expr.matches(ZonedDateTime.of(2026, 3, 28, 14, 30, 0, 0, UTC))).isFalse();
    }

    @Test
    void sundayMatching() {
        // Sunday = 0
        CronExpression expr = CronExpression.parse("0 0 * * 0");
        // 2026-05-16 is Saturday → false; 2026-05-17 is Sunday → true
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 16, 0, 0, 0, 0, UTC))).isFalse();
        assertThat(expr.matches(ZonedDateTime.of(2026, 5, 17, 0, 0, 0, 0, UTC))).isTrue();
    }

    @Test
    void commaListValues() {
        CronExpression expr = CronExpression.parse("0,15,30,45 * * * *");
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 15, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 30, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 45, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 10, 0, 0, UTC))).isFalse();
    }

    @Test
    void rangeWithStep() {
        CronExpression expr = CronExpression.parse("0-30/10 * * * *");
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 10, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 20, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 30, 0, 0, UTC))).isTrue();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 5, 0, 0, UTC))).isFalse();
        assertThat(expr.matches(ZonedDateTime.of(2026, 1, 1, 0, 40, 0, 0, UTC))).isFalse();
    }

    // -- nextFireTime --

    @Test
    void nextFireTimeBasic() {
        CronExpression expr = CronExpression.parse("30 9 * * *");
        ZonedDateTime from = ZonedDateTime.of(2026, 5, 15, 9, 0, 0, 0, UTC);
        ZonedDateTime next = expr.nextFireTime(from);
        assertThat(next).isNotNull();
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 5, 15, 9, 30, 0, 0, UTC));
    }

    @Test
    void nextFireTimeRollsToNextDay() {
        CronExpression expr = CronExpression.parse("0 8 * * *");
        ZonedDateTime from = ZonedDateTime.of(2026, 5, 15, 9, 0, 0, 0, UTC);
        ZonedDateTime next = expr.nextFireTime(from);
        assertThat(next).isNotNull();
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 5, 16, 8, 0, 0, 0, UTC));
    }

    @Test
    void nextFireTimeMonthBoundary() {
        CronExpression expr = CronExpression.parse("0 0 1 * *");
        ZonedDateTime from = ZonedDateTime.of(2026, 5, 15, 0, 0, 0, 0, UTC);
        ZonedDateTime next = expr.nextFireTime(from);
        assertThat(next).isNotNull();
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, UTC));
    }

    @Test
    void nextFireTimeSkipsCurrentMinute() {
        CronExpression expr = CronExpression.parse("* * * * *");
        ZonedDateTime from = ZonedDateTime.of(2026, 5, 15, 10, 30, 0, 0, UTC);
        ZonedDateTime next = expr.nextFireTime(from);
        assertThat(next).isNotNull();
        assertThat(next.getMinute()).isEqualTo(31);
    }

    @Test
    void nextFireTimeLeapYear() {
        // 2028 is a leap year
        CronExpression expr = CronExpression.parse("0 0 29 2 *");
        ZonedDateTime from = ZonedDateTime.of(2027, 3, 1, 0, 0, 0, 0, UTC);
        ZonedDateTime next = expr.nextFireTime(from);
        assertThat(next).isNotNull();
        assertThat(next).isEqualTo(ZonedDateTime.of(2028, 2, 29, 0, 0, 0, 0, UTC));
    }

    // -- invalid expressions --

    @Test
    void rejectsBlankExpression() {
        assertThatThrownBy(() -> CronExpression.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullExpression() {
        assertThatThrownBy(() -> CronExpression.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooFewFields() {
        assertThatThrownBy(() -> CronExpression.parse("* * *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 fields");
    }

    @Test
    void rejectsOutOfRangeMinute() {
        assertThatThrownBy(() -> CronExpression.parse("60 * * * *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void rejectsOutOfRangeHour() {
        assertThatThrownBy(() -> CronExpression.parse("0 24 * * *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void rejectsOutOfRangeDow() {
        assertThatThrownBy(() -> CronExpression.parse("0 0 * * 7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> CronExpression.parse("30-10 * * * *"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start");
    }

    @Test
    void rawPreserved() {
        CronExpression expr = CronExpression.parse("  */5  9  *  *  1-5  ");
        assertThat(expr.raw()).isEqualTo("*/5  9  *  *  1-5");
    }
}
