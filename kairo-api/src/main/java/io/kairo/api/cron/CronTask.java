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
 */
@Experimental("Cron task record — contract may change")
public record CronTask(
        String id,
        String cron,
        String prompt,
        Instant createdAt,
        @Nullable Instant lastFiredAt,
        boolean recurring,
        boolean durable) {

    public CronTask withLastFiredAt(Instant lastFiredAt) {
        return new CronTask(id, cron, prompt, createdAt, lastFiredAt, recurring, durable);
    }
}
