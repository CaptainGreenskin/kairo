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
package io.kairo.api.task;

/**
 * Lifecycle status of a task.
 *
 * @deprecated since v0.10 — see {@link Task}.
 */
@Deprecated(since = "0.10", forRemoval = true)
public enum TaskStatus {

    /** Task is created but not yet started. */
    PENDING,

    /** Task is actively being worked on. */
    IN_PROGRESS,

    /** Task has been completed successfully. */
    COMPLETED,

    /** Task failed during execution. */
    FAILED,

    /** Task was cancelled before completion. */
    CANCELLED,

    /** Task failed and exhausted all retry attempts. */
    ABANDONED
}
