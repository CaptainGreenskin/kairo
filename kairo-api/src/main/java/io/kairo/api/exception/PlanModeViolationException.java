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
 * Thrown when a tool with write or system-change side effects is invoked while the agent is in plan
 * mode.
 *
 * <p>Plan mode restricts the agent to read-only tools. This exception signals that the requested
 * tool is blocked until plan mode is exited.
 */
public class PlanModeViolationException extends ToolException {

    private static final String DEFAULT_ERROR_CODE = "PLAN_MODE_VIOLATION";

    private final String toolName;

    /**
     * Create a new PlanModeViolationException with the given message.
     *
     * @param message the detail message
     */
    public PlanModeViolationException(String message) {
        this(message, (String) null);
    }

    /**
     * Create a new PlanModeViolationException with the given message and tool name.
     *
     * @param message the detail message
     * @param toolName the name of the blocked tool
     */
    public PlanModeViolationException(String message, String toolName) {
        super(message, null, DEFAULT_ERROR_CODE);
        this.toolName = toolName;
    }

    /**
     * Create a new PlanModeViolationException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public PlanModeViolationException(String message, Throwable cause) {
        super(message, cause, DEFAULT_ERROR_CODE);
        this.toolName = null;
    }

    /**
     * Get the name of the tool that was blocked.
     *
     * @return the tool name, or null if not specified
     */
    public String getToolName() {
        return toolName;
    }
}
