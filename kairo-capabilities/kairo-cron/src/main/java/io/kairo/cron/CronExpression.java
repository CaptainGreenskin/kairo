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

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;

/**
 * Parses and evaluates standard 5-field cron expressions.
 *
 * <p>Format: {@code minute hour day-of-month month day-of-week}
 *
 * <p>Supported syntax per field: {@code *}, {@code N}, {@code N-M}, {@code N,M}, {@code *}{@code
 * /N}, {@code N-M/S}.
 */
public final class CronExpression {

    private static final int MAX_WALK_MINUTES = 366 * 24 * 60;

    private final String raw;
    private final BitSet minutes; // 0-59
    private final BitSet hours; // 0-23
    private final BitSet daysOfMonth; // 1-31
    private final BitSet months; // 1-12
    private final BitSet daysOfWeek; // 0-6  (0=Sunday)

    private CronExpression(
            String raw,
            BitSet minutes,
            BitSet hours,
            BitSet daysOfMonth,
            BitSet months,
            BitSet daysOfWeek) {
        this.raw = raw;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
    }

    public static CronExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron expression must not be blank");
        }
        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException(
                    "Cron expression must have exactly 5 fields (minute hour dom month dow), got "
                            + parts.length
                            + ": "
                            + expression);
        }
        BitSet minutes = parseField(parts[0], 0, 59, "minute");
        BitSet hours = parseField(parts[1], 0, 23, "hour");
        BitSet dom = parseField(parts[2], 1, 31, "day-of-month");
        BitSet months = parseField(parts[3], 1, 12, "month");
        BitSet dow = parseField(parts[4], 0, 6, "day-of-week");
        return new CronExpression(expression.trim(), minutes, hours, dom, months, dow);
    }

    public boolean matches(ZonedDateTime time) {
        int minute = time.getMinute();
        int hour = time.getHour();
        int dom = time.getDayOfMonth();
        int month = time.getMonthValue();
        int dow = toSundayZeroDow(time.getDayOfWeek());

        return minutes.get(minute)
                && hours.get(hour)
                && daysOfMonth.get(dom)
                && months.get(month)
                && daysOfWeek.get(dow);
    }

    /**
     * Finds the next minute (exclusive) at which this expression fires, walking forward from {@code
     * from} up to 366 days.
     *
     * @return the next fire time, or {@code null} if none found within 366 days
     */
    public ZonedDateTime nextFireTime(ZonedDateTime from) {
        ZonedDateTime candidate = from.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        ZonedDateTime limit = from.plusMinutes(MAX_WALK_MINUTES);

        while (candidate.isBefore(limit) || candidate.isEqual(limit)) {
            if (!months.get(candidate.getMonthValue())) {
                candidate = advanceMonth(candidate);
                continue;
            }
            if (!daysOfMonth.get(candidate.getDayOfMonth())
                    || !daysOfWeek.get(toSundayZeroDow(candidate.getDayOfWeek()))) {
                candidate = advanceDay(candidate);
                continue;
            }
            if (!hours.get(candidate.getHour())) {
                candidate = advanceHour(candidate);
                continue;
            }
            if (!minutes.get(candidate.getMinute())) {
                candidate = candidate.plusMinutes(1);
                continue;
            }
            return candidate;
        }
        return null;
    }

    public String raw() {
        return raw;
    }

    @Override
    public String toString() {
        return "CronExpression[" + raw + "]";
    }

    // -- field parsing --

    static BitSet parseField(String field, int min, int max, String name) {
        BitSet bits = new BitSet(max + 1);
        String[] alternatives = field.split(",");
        for (String alt : alternatives) {
            parseAlternative(alt.trim(), min, max, name, bits);
        }
        if (bits.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cron field '" + name + "' resolved to empty set: " + field);
        }
        return bits;
    }

    private static void parseAlternative(String token, int min, int max, String name, BitSet bits) {
        int slashIdx = token.indexOf('/');
        String rangeStr = slashIdx >= 0 ? token.substring(0, slashIdx) : token;
        int step = 1;

        if (slashIdx >= 0) {
            String stepStr = token.substring(slashIdx + 1);
            step = parseIntChecked(stepStr, name, "step");
            if (step < 1) {
                throw new IllegalArgumentException(
                        "Step in '" + name + "' must be >= 1, got: " + step);
            }
        }

        int rangeMin;
        int rangeMax;

        if (rangeStr.equals("*")) {
            rangeMin = min;
            rangeMax = max;
        } else if (rangeStr.contains("-")) {
            String[] parts = rangeStr.split("-", 2);
            rangeMin = parseIntChecked(parts[0], name, "range-start");
            rangeMax = parseIntChecked(parts[1], name, "range-end");
        } else {
            rangeMin = parseIntChecked(rangeStr, name, "value");
            rangeMax = slashIdx >= 0 ? max : rangeMin;
        }

        if (rangeMin < min || rangeMin > max) {
            throw new IllegalArgumentException(
                    "Value " + rangeMin + " out of range [" + min + "," + max + "] for " + name);
        }
        if (rangeMax < min || rangeMax > max) {
            throw new IllegalArgumentException(
                    "Value " + rangeMax + " out of range [" + min + "," + max + "] for " + name);
        }
        if (rangeMin > rangeMax) {
            throw new IllegalArgumentException(
                    "Range start " + rangeMin + " > end " + rangeMax + " in " + name);
        }

        for (int i = rangeMin; i <= rangeMax; i += step) {
            bits.set(i);
        }
    }

    private static int parseIntChecked(String s, String fieldName, String role) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid " + role + " '" + s + "' in cron field '" + fieldName + "'");
        }
    }

    // -- time advancement helpers --

    private static ZonedDateTime advanceMonth(ZonedDateTime t) {
        return t.withDayOfMonth(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .plusMonths(1);
    }

    private static ZonedDateTime advanceDay(ZonedDateTime t) {
        return t.withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1);
    }

    private static ZonedDateTime advanceHour(ZonedDateTime t) {
        return t.withMinute(0).withSecond(0).withNano(0).plusHours(1);
    }

    private static int toSundayZeroDow(DayOfWeek dow) {
        return dow.getValue() % 7; // Monday=1..Sunday=7 → Sunday=0, Monday=1..Saturday=6
    }
}
