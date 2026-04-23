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

import java.lang.annotation.*;

/**
 * Marks a {@link ToolHandler} implementation as non-idempotent — not safe to replay during
 * recovery.
 *
 * <p>When a durable execution is recovered after a crash, tools annotated with
 * {@code @NonIdempotent} will NOT be re-executed. Instead, the cached result from the execution
 * event log is returned.
 *
 * @since v0.8
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NonIdempotent {
    /** Reason this tool cannot be safely replayed. */
    String value() default "";
}
