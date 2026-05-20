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
package io.kairo.api.cron;

import io.kairo.api.Experimental;
import java.util.List;
import java.util.Optional;

/**
 * Manages cron-scheduled tasks: lifecycle, execution loop, and lifecycle ops (pause / resume / edit
 * / trigger).
 *
 * <p>SPI lives in {@code kairo-api}; the default implementation lives in {@code kairo-cron}.
 * Implementations must be thread-safe.
 *
 * @since 0.4
 */
@Experimental("Cron SPI promoted to kairo-api in v1.2 — shape may change")
public interface CronScheduler {

    /** Create a new task with default options (recurring=true|false, durable=true|false). */
    CronTask create(String cron, String prompt, boolean recurring, boolean durable);

    /**
     * Create a new task with the full option set: skills to pre-load, workdir, no-agent / script
     * mode, and task chaining. Default implementation falls through to the simpler 4-arg form
     * (drops the extras) so existing impls keep compiling — concrete schedulers should override.
     */
    default CronTask create(String cron, String prompt, CronTaskOptions options) {
        if (options == null) options = CronTaskOptions.defaults();
        return create(cron, prompt, options.recurring(), options.durable());
    }

    /** Delete a task by id. Returns whether one was removed. */
    boolean delete(String taskId);

    /** Snapshot of all currently registered tasks. */
    List<CronTask> list();

    /**
     * Pause a task (won't fire on subsequent ticks) without deleting it. Returns the updated task,
     * or empty when no task matches. No-op for already-paused tasks.
     */
    default Optional<CronTask> pause(String taskId) {
        return Optional.empty();
    }

    /** Resume a paused task; no-op when already running. */
    default Optional<CronTask> resume(String taskId) {
        return Optional.empty();
    }

    /**
     * Replace the cron expression and/or prompt of an existing task. Non-null fields are applied.
     */
    default Optional<CronTask> edit(String taskId, String newCron, String newPrompt) {
        return Optional.empty();
    }

    /**
     * Trigger a task to fire <em>now</em>, outside its normal schedule. The task's normal cadence
     * is unchanged. Returns whether the task was found.
     */
    default boolean trigger(String taskId) {
        return false;
    }

    /** Begin the tick loop. */
    void start();

    /** Halt the tick loop. Does not delete tasks. */
    void stop();
}
