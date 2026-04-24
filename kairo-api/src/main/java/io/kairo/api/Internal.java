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
package io.kairo.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks APIs that live under {@code io.kairo.api} for packaging / module-layer reasons but are
 * <em>not</em> part of Kairo's public contract.
 *
 * <p>{@code @Internal} elements may be removed, renamed, or have their semantics changed in any
 * release — including patch releases. User code that depends on them does so at its own risk.
 *
 * <p>Use this annotation sparingly. Prefer moving truly internal types out of {@code io.kairo.api}.
 * The two legitimate reasons to keep an internal type in {@code kairo-api}:
 *
 * <ul>
 *   <li>The type is referenced by an {@link Stable} or {@link Experimental} surface and cannot be
 *       relocated without breaking that surface.
 *   <li>The type is shared between {@code kairo-core} and another runtime module and has no natural
 *       host module.
 * </ul>
 *
 * @since v1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Internal {
    /** Optional rationale for why this lives in {@code kairo-api} despite being internal. */
    String value() default "";
}
