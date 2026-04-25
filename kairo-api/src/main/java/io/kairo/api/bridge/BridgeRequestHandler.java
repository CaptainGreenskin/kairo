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
package io.kairo.api.bridge;

import io.kairo.api.Stable;
import reactor.core.publisher.Mono;

/**
 * Application-side dispatcher: given a decoded {@link BridgeRequest} envelope, produce a {@link
 * BridgeResponse}.
 *
 * <p>The handler is the only abstraction the application has to implement to participate in the
 * bridge — the transport ({@link BridgeServer}) takes care of frame decoding, response correlation,
 * and error envelope generation. Handlers MUST return a non-null {@link Mono}; emit {@link
 * BridgeResponse#notFound(String)} for unknown ops rather than throwing.
 *
 * <p>A failing {@link Mono} is mapped by the transport to {@link BridgeResponse#internalError} with
 * the throwable message redacted to a short reason string.
 *
 * @since v1.1
 */
@FunctionalInterface
@Stable(since = "1.1.0", value = "Bridge request dispatcher SPI added in v1.1")
public interface BridgeRequestHandler {

    /**
     * Handle one bridge request. Implementations must not block the calling thread; offload heavy
     * work onto a scheduler.
     */
    Mono<BridgeResponse> handle(BridgeRequest request);
}
