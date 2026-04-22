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

/** Thrown when an agent's processing is interrupted (e.g., timeout, user cancel). */
public class AgentInterruptedException extends AgentException {

    private static final String DEFAULT_ERROR_CODE = "AGENT_INTERRUPTED";

    /**
     * Create a new AgentInterruptedException with the given message.
     *
     * @param message the detail message
     */
    public AgentInterruptedException(String message) {
        super(message, null, DEFAULT_ERROR_CODE);
    }

    /**
     * Create a new AgentInterruptedException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public AgentInterruptedException(String message, Throwable cause) {
        super(message, cause, DEFAULT_ERROR_CODE);
    }
}
