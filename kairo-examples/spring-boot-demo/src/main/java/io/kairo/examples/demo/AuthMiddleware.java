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
package io.kairo.examples.demo;

import io.kairo.api.middleware.Middleware;
import io.kairo.api.middleware.MiddlewareChain;
import io.kairo.api.middleware.MiddlewareContext;
import io.kairo.api.middleware.MiddlewareOrder;
import io.kairo.api.middleware.MiddlewareRejectException;
import reactor.core.publisher.Mono;

/**
 * Example middleware that validates an API key from request attributes.
 *
 * <p>Demonstrates how to write a {@link Middleware} for request-level authentication.
 * In a real application, the API key would be extracted from HTTP headers by a filter
 * and stored in the {@link MiddlewareContext#attributes()} map before reaching the agent.
 *
 * <pre>{@code
 * // Register as a Spring bean — the starter will auto-detect it
 * @Bean
 * public Middleware authMiddleware() {
 *     return new AuthMiddleware("my-secret-key");
 * }
 * }</pre>
 */
@MiddlewareOrder(after = {})
public class AuthMiddleware implements Middleware {

    private final String validApiKey;

    /** Create an auth middleware that accepts the given API key. */
    public AuthMiddleware(String validApiKey) {
        this.validApiKey = validApiKey;
    }

    @Override
    public String name() {
        return "auth";
    }

    @Override
    public Mono<MiddlewareContext> handle(MiddlewareContext context, MiddlewareChain chain) {
        String apiKey = (String) context.attributes().get("apiKey");

        if (apiKey == null || !apiKey.equals(validApiKey)) {
            return Mono.error(new MiddlewareRejectException("auth", "Invalid or missing API key"));
        }

        // Propagate user identity for downstream middleware
        return chain.next(context.withAttribute("authenticatedUser", "api-user"));
    }
}
