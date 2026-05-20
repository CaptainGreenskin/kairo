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
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * Immutable snapshot of a scheduled cron task.
 *
 * @param id 8-character hex identifier
 * @param cron 5-field cron expression (minute hour day-of-month month day-of-week)
 * @param prompt the prompt to enqueue when the task fires
 * @param createdAt when the task was created
 * @param lastFiredAt when the task last fired, or {@code null} if never fired
 * @param recurring {@code true} for repeating tasks, {@code false} for one-shot
 * @param durable {@code true} to persist across sessions, {@code false} for session-only
 * @param paused {@code true} when the task is suspended (next tick won't fire it)
 * @param consecutiveFailures count of back-to-back failures since the last success
 * @param lastError last error message (truncated), or {@code null}
 * @param nextRunAt next scheduled fire instant, or {@code null} when unknown / paused
 */
@Experimental("Cron task record — contract may change")
public record CronTask(
        String id,
        String cron,
        String prompt,
        Instant createdAt,
        @Nullable Instant lastFiredAt,
        boolean recurring,
        boolean durable,
        boolean paused,
        int consecutiveFailures,
        @Nullable String lastError,
        @Nullable Instant nextRunAt) {

    /** Backwards-compatible constructor — defaults the new fields. */
    public CronTask(
            String id,
            String cron,
            String prompt,
            Instant createdAt,
            @Nullable Instant lastFiredAt,
            boolean recurring,
            boolean durable) {
        this(id, cron, prompt, createdAt, lastFiredAt, recurring, durable, false, 0, null, null);
    }

    public CronTask withLastFiredAt(Instant lastFiredAt) {
        return new CronTask(
                id,
                cron,
                prompt,
                createdAt,
                lastFiredAt,
                recurring,
                durable,
                paused,
                consecutiveFailures,
                lastError,
                nextRunAt);
    }

    public CronTask withPaused(boolean paused) {
        return new CronTask(
                id,
                cron,
                prompt,
                createdAt,
                lastFiredAt,
                recurring,
                durable,
                paused,
                consecutiveFailures,
                lastError,
                nextRunAt);
    }

    public CronTask withCronAndPrompt(String newCron, String newPrompt) {
        return new CronTask(
                id,
                newCron == null ? cron : newCron,
                newPrompt == null ? prompt : newPrompt,
                createdAt,
                lastFiredAt,
                recurring,
                durable,
                paused,
                consecutiveFailures,
                lastError,
                nextRunAt);
    }

    public CronTask withStatus(int newConsecutiveFailures, @Nullable String newLastError) {
        return new CronTask(
                id,
                cron,
                prompt,
                createdAt,
                lastFiredAt,
                recurring,
                durable,
                paused,
                newConsecutiveFailures,
                newLastError,
                nextRunAt);
    }

    public CronTask withNextRunAt(@Nullable Instant when) {
        return new CronTask(
                id,
                cron,
                prompt,
                createdAt,
                lastFiredAt,
                recurring,
                durable,
                paused,
                consecutiveFailures,
                lastError,
                when);
    }
}
