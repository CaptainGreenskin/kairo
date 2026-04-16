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
package io.kairo.core.plan;

/**
 * Thrown when a tool with write or system-change side effects is invoked while the agent is in plan
 * mode.
 *
 * @deprecated Use {@link io.kairo.api.exception.PlanModeViolationException} instead. This class is
 *     kept for backward compatibility and delegates to the API exception hierarchy.
 */
@Deprecated
public class PlanModeViolationException extends io.kairo.api.exception.PlanModeViolationException {

    /**
     * Create a new exception with the given message.
     *
     * @param message the detail message
     */
    public PlanModeViolationException(String message) {
        this(message, null);
    }

    /**
     * Create a new exception with the given message and tool name.
     *
     * @param message the detail message
     * @param toolName the name of the blocked tool
     */
    public PlanModeViolationException(String message, String toolName) {
        super(message, toolName);
    }
}
