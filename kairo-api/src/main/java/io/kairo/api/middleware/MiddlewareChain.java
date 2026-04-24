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
 * Passes control to the next middleware in the pipeline.
 *
 * <p>A middleware that does <b>not</b> call {@link #next} short-circuits the pipeline — no
 * subsequent middleware or the agent itself will execute. @FunctionalInterface enables lambda usage
 * in tests and internal code.
 */
@FunctionalInterface
@Experimental(
        "Middleware chain SPI; shape pending v1.0 census review, targeting stabilization in v1.1")
public interface MiddlewareChain {

    /**
     * Invoke the next middleware in the pipeline with the given context.
     *
     * @param context the context to forward
     * @return a Mono emitting the context after all remaining middleware have processed it
     */
    Mono<MiddlewareContext> next(MiddlewareContext context);
}
