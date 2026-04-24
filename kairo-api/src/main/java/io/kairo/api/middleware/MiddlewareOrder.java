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
package io.kairo.api.middleware;

import io.kairo.api.Experimental;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative ordering for {@link Middleware} implementations using name-based anchors.
 *
 * <p>Replaces numeric {@code order()} values with explicit before/after constraints that are
 * validated at startup. Referenced names that don't exist or circular dependencies cause an
 * immediate failure.
 *
 * <pre>{@code
 * @MiddlewareOrder(after = {"auth"}, before = {"audit"})
 * public class RateLimitMiddleware implements Middleware {
 *     @Override
 *     public String name() { return "rate-limiter"; }
 *     // ...
 * }
 * }</pre>
 *
 * <p>Middleware without this annotation has no ordering constraint and may execute in any position
 * relative to ordered middleware.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Experimental(
        "Middleware ordering annotation; shape pending v1.0 census review, targeting stabilization in v1.1")
public @interface MiddlewareOrder {

    /**
     * Execute after the named middleware(s).
     *
     * @return middleware names that must execute before this one
     */
    String[] after() default {};

    /**
     * Execute before the named middleware(s).
     *
     * @return middleware names that must execute after this one
     */
    String[] before() default {};
}
