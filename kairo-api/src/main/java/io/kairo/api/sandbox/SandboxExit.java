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
package io.kairo.api.sandbox;

import io.kairo.api.Stable;
import javax.annotation.Nullable;

/**
 * Terminal status of a sandbox execution.
 *
 * <p>{@code exitCode} mirrors the OS process exit code where the backend supports it (0 = success,
 * non-zero = failure). When the process was killed by the backend (timeout, cancel, OOM), {@code
 * exitCode} MAY be {@code -1} and {@code signal} carries the backend-specific cause string (e.g.,
 * {@code "SIGKILL"}, {@code "TIMEOUT"}, {@code "CANCELLED"}). {@code timedOut} is the explicit
 * marker that the timeout from {@link SandboxRequest#timeout()} fired — set whenever the watchdog
 * killed the process for exceeding its budget.
 *
 * @param exitCode OS exit code, or {@code -1} when the process was killed by the backend
 * @param signal backend-specific cause descriptor, or {@code null} for normal exits
 * @param timedOut {@code true} iff the process was killed because {@link SandboxRequest#timeout()}
 *     elapsed
 * @param truncated {@code true} iff combined output exceeded {@link
 *     SandboxRequest#maxOutputBytes()} and was truncated
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Sandbox exit envelope added in v1.1")
public record SandboxExit(
        int exitCode, @Nullable String signal, boolean timedOut, boolean truncated) {}
