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

import io.kairo.api.exception.KairoException;

/**
 * Signals that a middleware has rejected the request, short-circuiting the pipeline.
 *
 * <p>Thrown (or returned via {@code Mono.error}) when a middleware decides the request should not
 * reach the agent — for example, authentication failure or rate limit exceeded.
 *
 * <pre>{@code
 * return Mono.error(new MiddlewareRejectException("auth", "Invalid API key"));
 * }</pre>
 *
 * <p>The agent runtime catches this exception and converts it to an error response, preventing the
 * ReAct loop from executing.
 */
public class MiddlewareRejectException extends KairoException {

    private final String middlewareName;

    /**
     * Create a rejection from the named middleware.
     *
     * @param middlewareName the {@link Middleware#name()} that rejected the request
     * @param reason human-readable rejection reason
     */
    public MiddlewareRejectException(String middlewareName, String reason) {
        super("Middleware [" + middlewareName + "] rejected: " + reason);
        this.middlewareName = middlewareName;
    }

    /** The name of the middleware that rejected the request. */
    public String middlewareName() {
        return middlewareName;
    }
}
