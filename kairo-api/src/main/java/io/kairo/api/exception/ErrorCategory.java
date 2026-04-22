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
package io.kairo.api.exception;

/**
 * Categorizes Kairo exceptions by subsystem origin.
 *
 * <p>Used as a structured field on {@link KairoException} to enable programmatic error routing,
 * metrics tagging, and guardrail decisions without relying on exception class hierarchy alone.
 *
 * <p><strong>Design note:</strong> The original plan proposed a TRANSIENT / PERMANENT / SECURITY
 * taxonomy. During implementation the enum evolved to subsystem-oriented categories (MODEL, TOOL,
 * AGENT, STORAGE, SECURITY, UNKNOWN), which proved more actionable for routing and metrics. See
 * ADR-008 for the rationale behind this divergence.
 */
public enum ErrorCategory {
    /** Model provider errors (rate limits, timeouts, API failures). */
    MODEL,
    /** Tool execution errors (permission denials, plan mode violations). */
    TOOL,
    /** Agent lifecycle errors (interruptions, execution failures). */
    AGENT,
    /** Storage errors (memory store failures, connection issues). */
    STORAGE,
    /** Security errors (guardrail denials, authentication failures). */
    SECURITY,
    /** Uncategorized or unknown errors. */
    UNKNOWN
}
