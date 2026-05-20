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
 * Marks a hook method to be invoked when the agent emits an out-of-band notification (long-running
 * task progress, permission prompts, etc.). Receives a {@link NotificationEvent}; handler return
 * value is ignored — this is a fire-and-forget channel for hosts and plugins.
 *
 * <p>Pair with Claude Code's {@code Notification} plugin hook event.
 */
@Experimental("Lifecycle hook annotation; introduced for plugin SPI v1.2; will stabilize with v1.3")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnNotification {
    /** Execution order. Lower values execute first. */
    int order() default 0;
}
