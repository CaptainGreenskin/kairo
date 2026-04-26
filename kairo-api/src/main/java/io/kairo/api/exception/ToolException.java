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

import io.kairo.api.Stable;

/** Base exception for tool execution errors such as permission denials and plan mode violations. */
@Stable(value = "Tool subsystem base exception; shape frozen since v0.7", since = "1.0.0")
public class ToolException extends KairoException {

    /**
     * Create a new ToolException with the given message.
     *
     * @param message the detail message
     */
    public ToolException(String message) {
        this(message, null, null);
    }

    /**
     * Create a new ToolException with the given message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public ToolException(String message, Throwable cause) {
        this(message, cause, null);
    }

    /**
     * Create a new ToolException with all structured error fields.
     *
     * @param message the detail message
     * @param cause the underlying cause (may be null)
     * @param errorCode machine-readable error code
     */
    protected ToolException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode, ErrorCategory.TOOL, false, null);
    }
}
