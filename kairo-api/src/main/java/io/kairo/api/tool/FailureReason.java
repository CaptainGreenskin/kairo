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
package io.kairo.api.tool;

import io.kairo.api.Stable;

/**
 * Categorizes why a tool execution did not produce a successful result. Stored under the {@link
 * #METADATA_KEY} key in {@link ToolResult#metadata()} so transports and UIs can render distinct
 * labels without parsing free-text error messages.
 *
 * <p>This is an additive observability convention — handlers MAY enrich error results with a
 * reason; consumers MUST treat absence as {@link #HANDLER_ERROR} (legacy behaviour).
 */
@Stable(value = "Tool failure taxonomy; additive only", since = "1.0.0")
public enum FailureReason {
    /** Tool exceeded its configured timeout. */
    TIMEOUT,
    /** User explicitly cancelled an in-flight tool (e.g. denied approval, pressed Stop). */
    USER_CANCELLED,
    /** Cooperative cancellation propagated from the agent loop. */
    INTERRUPTED,
    /** Tool handler threw an exception (any non-cancellation throwable). */
    HANDLER_ERROR,
    /** Input failed validation before the handler ran. */
    VALIDATION;

    /** Metadata key under which {@link FailureReason#name()} is stored on a {@link ToolResult}. */
    public static final String METADATA_KEY = "failureReason";
}
