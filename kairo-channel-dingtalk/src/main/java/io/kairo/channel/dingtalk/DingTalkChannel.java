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
package io.kairo.channel.dingtalk;

import io.kairo.api.Experimental;
import io.kairo.api.channel.Channel;
import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelFailureMode;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.api.channel.ChannelMessage;
import io.kairo.api.channel.ChannelOutboundSender;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * {@link Channel} adapter for DingTalk custom bots.
 *
 * <p>Inbound traffic arrives on the webhook endpoint owned by the starter's {@code
 * DingTalkWebhookController}; the controller dispatches verified messages through {@link
 * #dispatchInbound(ChannelMessage)}. Outbound replies flow through {@link #sender()}, which POSTs
 * to the bot's session webhook via {@link DingTalkOutboundClient}.
 *
 * <p>Idempotency: repeated deliveries of the same DingTalk {@code msgId} (the adapter's de-dup key)
 * are dropped silently; the handler sees each id at most once per channel lifetime. Applications
 * that need a persisted de-dup store can inject one — the default is an in-memory {@link Set}
 * bounded only by channel lifetime.
 *
 * @since v0.9.1 (Experimental)
 */
@Experimental("DingTalkChannel — contract may change in v0.10")
public final class DingTalkChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(DingTalkChannel.class);

    private final String id;
    private final DingTalkOutboundClient outboundClient;
    private final List<String> atMobiles;
    private final DingTalkMessageMapper mapper;

    private final AtomicReference<ChannelInboundHandler> handler = new AtomicReference<>();
    private final Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();
    private volatile boolean running = false;

    public DingTalkChannel(
            String id,
            DingTalkOutboundClient outboundClient,
            DingTalkMessageMapper mapper,
            List<String> atMobiles) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
        this.outboundClient = Objects.requireNonNull(outboundClient, "outboundClient");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.atMobiles = atMobiles == null ? List.of() : List.copyOf(atMobiles);
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
                                "DingTalkChannel '" + id + "' is already started");
                    }
                    running = true;
                    log.debug("DingTalkChannel '{}' started", id);
                });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(
                () -> {
                    running = false;
                    handler.set(null);
                    seenMessageIds.clear();
                    log.debug("DingTalkChannel '{}' stopped", id);
                });
    }

    @Override
    public ChannelOutboundSender sender() {
        return message -> {
            if (!running) {
                return Mono.just(
                        ChannelAck.fail(ChannelFailureMode.SEND_FAILED, "channel not running"));
            }
            return outboundClient.send(mapper.toDingTalkPayload(message, atMobiles));
        };
    }

    /**
     * Drive a verified inbound message into the registered handler. Deduplicates by DingTalk {@code
     * msgId} so replayed webhook deliveries are coalesced. Callers (the webhook controller) should
     * only invoke this after signature verification succeeds.
     *
     * @return the handler's ack, or a synthetic success ack when the message was deduplicated, or
     *     {@link ChannelFailureMode#REJECTED} when no handler is registered.
     */
    public Mono<ChannelAck> dispatchInbound(ChannelMessage message) {
        Objects.requireNonNull(message, "message");
        ChannelInboundHandler h = handler.get();
        if (h == null) {
            return Mono.just(
                    ChannelAck.fail(ChannelFailureMode.REJECTED, "no inbound handler registered"));
        }
        String msgId =
                message.attributes().getOrDefault(DingTalkMessageMapper.ATTR_MSG_ID, message.id());
        if (!seenMessageIds.add(msgId)) {
            log.debug(
                    "DingTalkChannel '{}' dropped duplicate msgId {} (idempotency replay)",
                    id,
                    msgId);
            return Mono.just(ChannelAck.ok(msgId));
        }
        return h.onInbound(message);
    }

    /**
     * Convenience: parse {@code rawJson} using the channel's mapper and dispatch. Exposed primarily
     * for the webhook controller and TCK.
     */
    public Mono<ChannelAck> dispatchInbound(String rawJson) {
        ChannelMessage message = mapper.fromDingTalkPayload(rawJson);
        return dispatchInbound(message);
    }

    public boolean isRunning() {
        return running;
    }

    /** Visible for testing. */
    DingTalkMessageMapper mapper() {
        return mapper;
    }

    /** Backstop for leak tests; 0 when stopped. */
    public int dedupSetSize() {
        return seenMessageIds.size();
    }

    /** Convenience factory for tests. */
    public static Duration defaultTimeout() {
        return Duration.ofSeconds(10);
    }
}
