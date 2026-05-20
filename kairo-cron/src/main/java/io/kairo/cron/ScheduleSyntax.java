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

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Hermes-style interval / relative schedule syntax into a 5-field cron expression so the
 * existing {@link CronExpression} engine handles it.
 *
 * <p>Supported shapes:
 *
 * <ul>
 *   <li>{@code "every 5m"} / {@code "every 30s"} / {@code "every 2h"} / {@code "every 1d"} → maps
 *       to the closest cron expression. Sub-minute is rejected (we tick every 60 s) — use the
 *       explicit cron form if you need finer-grained scheduling.
 *   <li>{@code "every 1d at 09:00"} / {@code "every 1d at 9:30"} → daily at the given time.
 *   <li>5-field cron strings pass through unchanged.
 * </ul>
 */
public final class ScheduleSyntax {

    private static final Pattern INTERVAL =
            Pattern.compile(
                    "^every\\s+(\\d+)\\s*([smhd])(?:\\s+at\\s+(\\d{1,2}):(\\d{2}))?$",
                    Pattern.CASE_INSENSITIVE);

    private ScheduleSyntax() {}

    /**
     * Returns {@code null} when {@code input} looks like a plain 5-field cron (lets the caller pass
     * it straight to {@link CronExpression#parse(String)}). Otherwise returns the translated cron.
     *
     * @throws IllegalArgumentException for unsupported interval shapes
     */
    public static String toCron(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Schedule must not be blank");
        }
        String trimmed = input.trim();
        // Heuristic: cron expressions have 5 whitespace-separated tokens.
        if (looksLikeCron(trimmed)) {
            return trimmed;
        }
        Matcher m = INTERVAL.matcher(trimmed);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Unrecognised schedule '"
                            + input
                            + "'. Use a 5-field cron expression (e.g. \"0 9 * * *\") or"
                            + " interval syntax (e.g. \"every 5m\", \"every 1d at 09:00\").");
        }
        long amount = Long.parseLong(m.group(1));
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        Duration interval = unitToDuration(unit, amount);

        // Anchor time (HH:MM) — only meaningful with day-grain intervals.
        String hhmm = null;
        if (m.group(3) != null) {
            int hh = Integer.parseInt(m.group(3));
            int mm = Integer.parseInt(m.group(4));
            if (hh < 0 || hh > 23 || mm < 0 || mm > 59) {
                throw new IllegalArgumentException(
                        "Invalid time-of-day '" + m.group(3) + ":" + m.group(4) + "'");
            }
            hhmm = hh + ":" + String.format("%02d", mm);
        }

        return buildCron(interval, hhmm, input);
    }

    private static boolean looksLikeCron(String trimmed) {
        String[] parts = trimmed.split("\\s+");
        if (parts.length != 5) return false;
        // First token of a cron always contains digits / star / step — interval starts with
        // "every".
        return !parts[0].toLowerCase(Locale.ROOT).equals("every");
    }

    private static Duration unitToDuration(String unit, long amount) {
        return switch (unit) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
        };
    }

    private static String buildCron(Duration interval, String hhmm, String original) {
        if (interval.toSeconds() < 60) {
            throw new IllegalArgumentException(
                    "Sub-minute intervals not supported (tick granularity is 60s); got '"
                            + original
                            + "'");
        }
        long totalMinutes = interval.toMinutes();
        if (hhmm != null) {
            // Day-anchor: must be a whole number of days.
            long days = interval.toDays();
            if (days < 1 || interval.toMinutes() != days * 24L * 60L) {
                throw new IllegalArgumentException(
                        "'at HH:MM' anchor requires a day-grain interval; got '" + original + "'");
            }
            String[] parts = hhmm.split(":");
            String hour = parts[0];
            String minute = parts[1];
            // every 1d at HH:MM → "MM HH * * *"; every 2d at HH:MM is not expressible in 5-field
            // cron without restricting day-of-month, so we accept only 1d.
            if (days != 1) {
                throw new IllegalArgumentException(
                        "'every Nd at HH:MM' currently supports only N=1; got " + days);
            }
            return minute + " " + hour + " * * *";
        }
        // No HH:MM anchor — pick the right field by granularity.
        if (totalMinutes < 60) {
            return "*/" + totalMinutes + " * * * *";
        }
        long totalHours = interval.toHours();
        if (totalHours < 24 && totalMinutes % 60 == 0) {
            return "0 */" + totalHours + " * * *";
        }
        long totalDays = interval.toDays();
        if (totalDays >= 1 && totalDays <= 31 && interval.toMinutes() == totalDays * 24L * 60L) {
            return "0 0 */" + totalDays + " * *";
        }
        throw new IllegalArgumentException(
                "Interval '"
                        + original
                        + "' doesn't map cleanly to a 5-field cron; use an explicit cron"
                        + " expression instead.");
    }
}
