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
package io.kairo.api.sandbox;

import io.kairo.api.Stable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Live handle to a running sandbox execution.
 *
 * <p>Output streaming is hot: subscribing to {@link #output()} after the process has produced data
 * MUST NOT replay earlier chunks (implementations typically multicast through a {@code
 * Sinks.Many.multicast().onBackpressureBuffer()}). {@link #exit()} caches the terminal {@link
 * SandboxExit} so it is safe to subscribe at any time, including after the process has already
 * exited.
 *
 * <p>{@link #cancel()} is idempotent and triggers process termination; the {@link #exit()} mono
 * MUST then complete with {@code timedOut = false} and {@code signal} set to whatever the backend
 * uses for cancellation. {@link #close()} releases backend resources (file handles, watchdog
 * threads, container) and is also idempotent; closing a still-running execution implies {@link
 * #cancel()}.
 *
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Sandbox handle added in v1.1")
public interface SandboxHandle extends AutoCloseable {

    /** Hot stream of stdout / stderr chunks. Completes when the process exits. */
    Flux<SandboxOutputChunk> output();

    /**
     * Cached terminal exit; completes once when the process finishes (normally, killed, or
     * cancelled).
     */
    Mono<SandboxExit> exit();

    /**
     * Idempotent cancellation; the process is terminated and {@link #exit()} eventually completes.
     */
    void cancel();

    /** Idempotent resource release. Implies {@link #cancel()} if the process is still running. */
    @Override
    void close();
}
