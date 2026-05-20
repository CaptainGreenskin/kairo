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

import io.kairo.api.cron.CronTask;
import io.kairo.api.cron.CronTaskOptions;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Locks down the M3 extras: skills, workdir, no-agent mode, and task-chain pointer. They survive
 * the create() → tick() → callback round-trip so a host (assistant) can use them to drive
 * Hermes-style behaviour.
 */
class CronTaskOptionsTest {

    @Test
    void optionsBuilderSetsAllFields() {
        var opts =
                CronTaskOptions.builder()
                        .recurring(true)
                        .durable(true)
                        .skills(List.of("audit", "summarise"))
                        .workdir("/var/tmp/proj")
                        .noAgent(false)
                        .contextFromTaskId("prev-task")
                        .build();
        assertThat(opts.recurring()).isTrue();
        assertThat(opts.durable()).isTrue();
        assertThat(opts.skills()).containsExactly("audit", "summarise");
        assertThat(opts.workdir()).isEqualTo("/var/tmp/proj");
        assertThat(opts.noAgent()).isFalse();
        assertThat(opts.contextFromTaskId()).isEqualTo("prev-task");
    }

    @Test
    void schedulerSurfacesOptionsOnTask(@TempDir Path tmp) {
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> {},
                        ZoneId.systemDefault());
        var opts =
                CronTaskOptions.builder()
                        .recurring(true)
                        .skills(List.of("alpha"))
                        .workdir("/work")
                        .contextFromTaskId("upstream")
                        .build();
        CronTask t = sched.create("0 * * * *", "p", opts);

        assertThat(t.skills()).containsExactly("alpha");
        assertThat(t.workdir()).isEqualTo("/work");
        assertThat(t.contextFromTaskId()).isEqualTo("upstream");
    }

    @Test
    void noAgentWithoutScriptRejected(@TempDir Path tmp) {
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> {},
                        ZoneId.systemDefault());
        var opts = CronTaskOptions.builder().recurring(true).noAgent(true).build();
        assertThatThrownBy(() -> sched.create("0 * * * *", "p", opts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("script");
    }

    @Test
    void noAgentWithScriptStoredVerbatim(@TempDir Path tmp) {
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> {},
                        ZoneId.systemDefault());
        var opts =
                CronTaskOptions.builder()
                        .recurring(true)
                        .noAgent(true)
                        .script("#!/bin/sh\necho hi")
                        .build();
        CronTask t = sched.create("0 * * * *", "ignored", opts);
        assertThat(t.noAgent()).isTrue();
        assertThat(t.script()).contains("echo hi");
    }

    @Test
    void noAgentFireReachesCallbackWithFlagSet(@TempDir Path tmp) {
        AtomicReference<CronTask> captured = new AtomicReference<>();
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> captured.set(task),
                        ZoneId.systemDefault());
        CronTask t =
                sched.create(
                        "0 * * * *",
                        "ignored-in-no-agent",
                        CronTaskOptions.builder()
                                .recurring(true)
                                .noAgent(true)
                                .script("echo hi")
                                .build());
        sched.trigger(t.id());
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().noAgent()).isTrue();
        assertThat(captured.get().script()).isEqualTo("echo hi");
    }

    @Test
    void backwardsCompatibleCreateStillWorks(@TempDir Path tmp) {
        var sched =
                new DefaultCronScheduler(
                        new CronTaskStore(tmp.resolve("tasks.json")),
                        task -> {},
                        ZoneId.systemDefault());
        CronTask t = sched.create("0 * * * *", "p", true, false);
        // M3 fields default to neutral values.
        assertThat(t.skills()).isEmpty();
        assertThat(t.workdir()).isNull();
        assertThat(t.noAgent()).isFalse();
        assertThat(t.script()).isNull();
        assertThat(t.contextFromTaskId()).isNull();
    }
}
