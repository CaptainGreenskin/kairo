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
 * Marks APIs that are part of Kairo's stable public contract.
 *
 * <p>Signatures, semantics, and existence of {@code @Stable} types, methods, and fields are frozen
 * across minor releases. Breaking changes are only allowed at major version boundaries (e.g., v1.0
 * → v2.0) with explicit migration notes.
 *
 * <p>Rules for maintainers:
 *
 * <ul>
 *   <li>Do not delete, rename, or change signatures of {@code @Stable} elements between minor
 *       releases.
 *   <li>Additive changes (new default methods on interfaces, new fields with defaults, new enum
 *       values at the tail) are permitted.
 *   <li>A {@code @Stable} element may never be downgraded to {@link Experimental} or {@link
 *       Internal} within a major version.
 *   <li>Removal requires a full deprecation cycle: mark {@code @Deprecated(forRemoval=true)} in
 *       vN.x, remove in v(N+1).0.
 * </ul>
 *
 * <p>Rules for users:
 *
 * <ul>
 *   <li>It is safe to implement, extend, and depend on {@code @Stable} SPIs.
 *   <li>Compile-time breaks across minor releases on a {@code @Stable} surface are bugs — open an
 *       issue.
 * </ul>
 *
 * @since v1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Stable {
    /** Optional rationale, e.g., "Core ReAct contract; shipped since v0.1 and unchanged". */
    String value() default "";

    /** The version in which this element reached stable status (e.g., "1.0.0"). */
    String since() default "1.0.0";
}
