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
package io.kairo.api.channel;

import io.kairo.api.Experimental;
import reactor.core.publisher.Mono;

/**
 * Application-side contract for handling messages that arrive on a {@link Channel}. The adapter
 * calls {@link #onInbound(ChannelMessage)} once per delivered message; the application decides how
 * to route it (agent invocation, expert-team run, queue, etc.).
 *
 * <p><b>Ordering:</b> adapters MUST serialize calls per {@link ChannelIdentity#destination()} to
 * preserve per-conversation ordering. They MAY parallelise across distinct destinations.
 *
 * <p><b>Error handling:</b> handlers should never throw across the SPI boundary — the returned
 * {@link Mono} is expected to complete successfully or emit a {@link ChannelAck#fail} when the
 * message could not be processed.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("Channel SPI — contract may change in v0.10")
@FunctionalInterface
public interface ChannelInboundHandler {

    /** Process one inbound message; the returned ack is reported back to the adapter. */
    Mono<ChannelAck> onInbound(ChannelMessage message);
}
