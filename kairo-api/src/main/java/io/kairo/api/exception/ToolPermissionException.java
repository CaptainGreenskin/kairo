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

/** Thrown when a tool execution is denied by the permission guard. */
public class ToolPermissionException extends ToolException {

    private static final String DEFAULT_ERROR_CODE = "TOOL_PERMISSION_DENIED";

    /**
     * Create a new ToolPermissionException with the given message.
     *
     * @param message the detail message
     */
    public ToolPermissionException(String message) {
        super(message, null, DEFAULT_ERROR_CODE);
    }

    /**
     * Create a new ToolPermissionException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ToolPermissionException(String message, Throwable cause) {
        super(message, cause, DEFAULT_ERROR_CODE);
    }
}
