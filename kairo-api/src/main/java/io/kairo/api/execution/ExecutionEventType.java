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

import io.kairo.api.Experimental;

/**
 * Type of event recorded in a durable execution's event log.
 *
 * @since v0.8 (Experimental)
 */
@Experimental("DurableExecution SPI — contract may change in v0.9")
public enum ExecutionEventType {

    /** An LLM call request was issued. */
    MODEL_CALL_REQUEST,

    /** An LLM call response was received. */
    MODEL_CALL_RESPONSE,

    /** A tool invocation request was issued. */
    TOOL_CALL_REQUEST,

    /** A tool invocation response was received. */
    TOOL_CALL_RESPONSE,

    /** The conversation context was compacted. */
    CONTEXT_COMPACTED,

    /** One full ReAct iteration completed. */
    ITERATION_COMPLETE
}
