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
package io.kairo.api.hook;

import io.kairo.api.Experimental;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a hook method to be invoked when a user submits a prompt to the agent. Fires before any
 * reasoning, after input validation. Receives a {@link UserPromptSubmitEvent}; the handler can
 * return the (possibly modified) event to enrich/redact the prompt before the agent sees it.
 *
 * <p>Pair with Claude Code's {@code UserPromptSubmit} plugin hook event.
 */
@Experimental("Lifecycle hook annotation; introduced for plugin SPI v1.2; will stabilize with v1.3")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnUserPromptSubmit {
    /** Execution order. Lower values execute first. */
    int order() default 0;
}
