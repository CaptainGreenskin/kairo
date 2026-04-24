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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a tool implementation.
 *
 * <p>Annotated classes will be discovered during classpath scanning and registered into the {@link
 * ToolRegistry}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Stable(value = "Tool discovery annotation; shape unchanged since v0.1", since = "1.0.0")
public @interface Tool {

    /** The tool name exposed to the model. */
    String name();

    /** A description of what this tool does. */
    String description();

    /** The tool category for grouping. */
    ToolCategory category() default ToolCategory.GENERAL;

    /** Timeout in seconds for this tool. 0 means use the default. */
    long timeoutSeconds() default 0;

    /** The side-effect classification of this tool. Defaults to {@code READ_ONLY}. */
    ToolSideEffect sideEffect() default ToolSideEffect.READ_ONLY;

    /**
     * Optional usage guidance hint appended to the tool description in the system prompt.
     *
     * <p>Examples:
     *
     * <ul>
     *   <li>"Use for quick file reads; for large files use GrepTool instead"
     *   <li>"Danger: may modify system state — confirm before running destructive commands"
     * </ul>
     */
    String usageGuidance() default "";
}
