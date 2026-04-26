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
 * Unified hook-handler annotation that replaces the per-phase {@code @OnX} / {@code @PreX} /
 * {@code @PostX} family. The legacy annotations continue to work for v0.10 but new code should
 * prefer {@code @HookHandler(phase)}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @HookHandler(HookPhase.SESSION_END)
 * void onSessionEnd(SessionEndEvent event) { ... }
 * }</pre>
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Unified hook dispatch — contract may change in v0.11")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HookHandler {

    /** The lifecycle phase this method listens to. */
    HookPhase value();

    /**
     * Optional ordering; lower values fire first. Matches the semantics of the legacy {@code
     * order()} attributes on {@code @OnSessionEnd} etc.
     */
    int order() default 0;
}
