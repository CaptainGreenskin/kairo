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
package io.kairo.channel;

import io.kairo.api.Experimental;
import io.kairo.api.channel.Channel;
import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelFailureMode;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.api.channel.ChannelMessage;
import io.kairo.api.channel.ChannelOutboundSender;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * In-memory reference {@link Channel} for tests and demos. Messages sent via {@link #sender()} are
 * captured in an in-memory buffer and can be replayed through {@link #simulateInbound} to drive
 * agents / expert teams without touching any real transport.
 *
 * <p>Not intended for production use — no retries, no ordering guarantees beyond what Reactor gives
 * you, no persistence.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("LoopbackChannel — contract may change in v0.10")
public final class LoopbackChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(LoopbackChannel.class);

    private final String id;
    private final AtomicReference<ChannelInboundHandler> handler = new AtomicReference<>();
    private final CopyOnWriteArrayList<ChannelMessage> outboundLog = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;

    public LoopbackChannel(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Mono<Void> start(ChannelInboundHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return Mono.fromRunnable(
                () -> {
                    if (!this.handler.compareAndSet(null, handler)) {
                        throw new IllegalStateException(
                                "LoopbackChannel '" + id + "' is already started");
                    }
                    running = true;
                    log.debug("LoopbackChannel '{}' started", id);
                });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(
                () -> {
                    running = false;
                    handler.set(null);
                    log.debug("LoopbackChannel '{}' stopped", id);
                });
    }

    @Override
    public ChannelOutboundSender sender() {
        return message -> {
            if (!running) {
                return Mono.just(
                        ChannelAck.fail(ChannelFailureMode.SEND_FAILED, "channel not running"));
            }
            outboundLog.add(message);
            return Mono.just(ChannelAck.ok(message.id()));
        };
    }

    /**
     * Drive an inbound message through the registered handler. Primarily used by tests and the TCK;
     * returns the handler's ack so callers can assert on it.
     */
    public Mono<ChannelAck> simulateInbound(ChannelMessage message) {
        ChannelInboundHandler h = handler.get();
        if (h == null) {
            return Mono.just(
                    ChannelAck.fail(ChannelFailureMode.REJECTED, "no inbound handler registered"));
        }
        return h.onInbound(message);
    }

    /** Immutable snapshot of every outbound message sent since {@link #start}. */
    public List<ChannelMessage> outboundLog() {
        return List.copyOf(outboundLog);
    }

    /** Clear the captured outbound log (useful between test cases). */
    public void clearOutboundLog() {
        outboundLog.clear();
    }

    public boolean isRunning() {
        return running;
    }
}
