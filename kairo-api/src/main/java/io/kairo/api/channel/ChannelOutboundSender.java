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
 * Adapter-side contract for pushing a reply back onto the channel. The application obtains a sender
 * from the runtime (typically via {@link Channel#sender()}) and calls {@link #send(ChannelMessage)}
 * with the outbound message; the returned {@link ChannelAck} surfaces transport-level success or
 * failure.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("Channel SPI — contract may change in v0.10")
@FunctionalInterface
public interface ChannelOutboundSender {

    /**
     * Send one outbound message. Implementations MUST NOT throw across the SPI boundary — any
     * transport failure is reported via {@link ChannelAck#fail(ChannelFailureMode, String)}.
     */
    Mono<ChannelAck> send(ChannelMessage message);
}
