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
import reactor.core.publisher.Mono;

/**
 * Generic request interceptor for cross-cutting concerns that run <b>before</b> the agent loop.
 *
 * <p>Middleware handles infrastructure-level concerns (authentication, rate limiting, audit
 * logging) that are independent of the agent's internal lifecycle. For agent-level behavioral
 * decisions (skip reasoning, modify model config, inject context), use the {@code Hook} system
 * instead.
 *
 * <h3>Middleware vs Hook boundary</h3>
 *
 * <table>
 *   <tr><th>Concern</th><th>Use Middleware</th><th>Use Hook</th></tr>
 *   <tr><td>Authentication / Authorization</td><td>Yes</td><td>No</td></tr>
 *   <tr><td>Rate limiting</td><td>Yes</td><td>No</td></tr>
 *   <tr><td>Audit logging</td><td>Yes</td><td>No</td></tr>
 *   <tr><td>Modify model config</td><td>No</td><td>Yes ({@code @PreReasoning} MODIFY)</td></tr>
 *   <tr><td>Skip tool execution</td><td>No</td><td>Yes ({@code @PreActing} SKIP)</td></tr>
 *   <tr><td>Inject conversation context</td><td>No</td><td>Yes (INJECT)</td></tr>
 * </table>
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * @MiddlewareOrder(before = {"rate-limiter", "audit"})
 * public class AuthMiddleware implements Middleware {
 *     @Override
 *     public String name() { return "auth"; }
 *
 *     @Override
 *     public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
 *         String token = (String) ctx.attributes().get("token");
 *         if (token == null) {
 *             return Mono.error(new MiddlewareRejectException("auth", "Unauthorized"));
 *         }
 *         return chain.next(ctx.withAttribute("user", validate(token)));
 *     }
 * }
 * }</pre>
 *
 * @see MiddlewareChain
 * @see MiddlewareContext
 * @see MiddlewareOrder
 * @see MiddlewareRejectException
 */
@Experimental("Middleware SPI; shape pending v1.0 census review, targeting stabilization in v1.1")
public interface Middleware {

    /**
     * Unique name for this middleware, used by {@link MiddlewareOrder#after()} and {@link
     * MiddlewareOrder#before()} for declarative positioning.
     *
     * @return non-null, non-empty identifier
     */
    String name();

    /**
     * Process the request. Call {@code chain.next(context)} to pass to the next middleware, or
     * return directly to short-circuit the pipeline.
     *
     * <p>To reject the request, return {@code Mono.error(new MiddlewareRejectException(...))}.
     *
     * @param context the current request context
     * @param chain the chain for forwarding to the next middleware
     * @return a Mono emitting the (possibly modified) context
     */
    Mono<MiddlewareContext> handle(MiddlewareContext context, MiddlewareChain chain);
}
