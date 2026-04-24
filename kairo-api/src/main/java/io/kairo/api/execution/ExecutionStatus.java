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
package io.kairo.api.execution;

import io.kairo.api.Stable;

/**
 * Status of a durable execution.
 *
 * @since v0.8 (promoted to @Stable in v1.0.0)
 */
@Stable(
        value = "Execution status enum; values frozen since v0.8, promoted post-v0.9 GA",
        since = "1.0.0")
public enum ExecutionStatus {

    /** The execution is actively running. */
    RUNNING,

    /** The execution has been paused (e.g. awaiting human approval). */
    PAUSED,

    /** The execution completed successfully. */
    COMPLETED,

    /** The execution terminated due to an error. */
    FAILED,

    /** The execution is being recovered from a crash or restart. */
    RECOVERING
}
