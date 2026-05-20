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

import io.kairo.api.cron.CronTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locks down the 4 P0 reliability features added in M1: kill switch, idle gate, deterministic
 * jitter, and pause-skip-during-tick.
 */
class DefaultCronSchedulerP0Test {

    @Test
    void killSwitchEnvSkipsTick(@TempDir Path tmp) {
        AtomicInteger fires = new AtomicInteger();
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> fires.incrementAndGet(),
                        ZoneId.systemDefault());
        // Add a task that should fire every minute.
        sched.create("* * * * *", "do thing", true, false);
        // Flip the kill switch via system property.
        try {
            System.setProperty("kairo.cron.disabled", "true");
            sched.tick();
            assertThat(fires.get()).as("kill switch must skip the tick").isZero();
        } finally {
            System.clearProperty("kairo.cron.disabled");
        }
    }

    @Test
    void idleGateFalseSkipsTick(@TempDir Path tmp) {
        AtomicInteger fires = new AtomicInteger();
        AtomicBoolean idle = new AtomicBoolean(false);
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> fires.incrementAndGet(),
                        ZoneId.systemDefault(),
                        idle::get);
        sched.create("* * * * *", "do thing", true, false);
        sched.tick();
        assertThat(fires.get()).as("idle gate false → no fires").isZero();
        idle.set(true);
        sched.tick();
        assertThat(fires.get()).as("idle gate true → fires").isPositive();
    }

    @Test
    void pausedTaskNotFiredEvenWhenScheduleMatches(@TempDir Path tmp) {
        AtomicInteger fires = new AtomicInteger();
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> fires.incrementAndGet(),
                        ZoneId.systemDefault());
        CronTask task = sched.create("* * * * *", "p", true, false);
        sched.pause(task.id());
        sched.tick();
        assertThat(fires.get()).as("paused task must not fire").isZero();

        sched.resume(task.id());
        sched.tick();
        assertThat(fires.get()).as("resumed task fires again").isPositive();
    }

    @Test
    void jitterIsDeterministicAndBounded() {
        CronTask recurring =
                new CronTask("deadbeef", "*/5 * * * *", "p", Instant.now(), null, true, false);
        long first = DefaultCronScheduler.computeJitterMs(recurring);
        long second = DefaultCronScheduler.computeJitterMs(recurring);
        assertThat(first).isEqualTo(second).as("deterministic by task id");
        // 5-minute period → 10% = 30s = 30_000 ms cap (well below 15-min cap).
        assertThat(first).isBetween(0L, 30_000L);

        // One-shot tasks: no jitter (we let the tool prompt steer users away from :00/:30).
        CronTask oneShot =
                new CronTask("feedface", "30 9 * * *", "q", Instant.now(), null, false, false);
        assertThat(DefaultCronScheduler.computeJitterMs(oneShot)).isZero();
    }

    @Test
    void editChangesCronAndPromptAndPersists(@TempDir Path tmp) {
        AtomicInteger fires = new AtomicInteger();
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> fires.incrementAndGet(),
                        ZoneId.systemDefault());
        CronTask t = sched.create("0 9 * * *", "morning", true, true);
        var edited = sched.edit(t.id(), "0 10 * * *", "later morning").orElseThrow();
        assertThat(edited.cron()).isEqualTo("0 10 * * *");
        assertThat(edited.prompt()).isEqualTo("later morning");
        // Durable change should hit disk.
        Path file = tmp.resolve("tasks.json");
        assertThat(Files.isRegularFile(file)).isTrue();
    }

    @Test
    void triggerFiresOutsideSchedule(@TempDir Path tmp) {
        AtomicInteger fires = new AtomicInteger();
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> fires.incrementAndGet(),
                        ZoneId.systemDefault());
        // A task that never matches (Feb 30 doesn't exist) — only `trigger` can fire it.
        CronTask t = sched.create("0 0 1 1 *", "new year", true, false);
        assertThat(fires.get()).isZero();
        assertThat(sched.trigger(t.id())).isTrue();
        assertThat(fires.get()).isOne();
    }
}
