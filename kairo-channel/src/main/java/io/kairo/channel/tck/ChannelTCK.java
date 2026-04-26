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
package io.kairo.channel.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.channel.Channel;
import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelFailureMode;
import io.kairo.api.channel.ChannelIdentity;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.api.channel.ChannelMessage;
import io.kairo.api.channel.ChannelOutboundSender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Abstract contract test kit for {@link Channel} implementations.
 *
 * <p>Third-party adapter authors extend this class and implement {@link #newChannel()} plus (for
 * scenario 2) {@link #newFailingSender(Channel)} so the kit can drive the three minimum scenarios
 * from the v0.9 Channel SPI scoping doc:
 *
 * <ol>
 *   <li>inbound message → registered handler → ack surfaced
 *   <li>outbound transport failure is reported as {@link ChannelFailureMode#SEND_FAILED}
 *   <li>concurrent inbound messages on the same {@link ChannelIdentity#destination()} are delivered
 *       to the handler in submission order (the SPI contract delegates per-destination ordering to
 *       the adapter)
 * </ol>
 *
 * @since v0.9 (Experimental)
 */
public abstract class ChannelTCK {

    /** Factory hook: each scenario starts with a freshly constructed, not-yet-started channel. */
    protected abstract Channel newChannel();

    /**
     * Scenario 2 hook: return an {@link ChannelOutboundSender} wrapping (or replacing) {@code
     * channel.sender()} that deterministically surfaces {@link ChannelFailureMode#SEND_FAILED}.
     * Default implementation wraps the real sender with one that always fails, which is sufficient
     * for any adapter that doesn't need transport-level fault injection.
     */
    protected ChannelOutboundSender newFailingSender(Channel channel) {
        return message ->
                Mono.just(
                        ChannelAck.fail(
                                ChannelFailureMode.SEND_FAILED, "TCK-injected transport failure"));
    }

    // ------------------------------------------------------------------ scenario 1

    @Test
    void inboundMessage_isDispatched_toHandler_andAckIsReturned() {
        Channel channel = newChannel();
        List<ChannelMessage> received = new CopyOnWriteArrayList<>();
        ChannelInboundHandler handler =
                message -> {
                    received.add(message);
                    return Mono.just(ChannelAck.ok("remote-" + message.id()));
                };

        channel.start(handler).block(Duration.ofSeconds(2));

        ChannelMessage inbound =
                ChannelMessage.of(ChannelIdentity.of(channel.id(), "peer-1"), "hello");
        ChannelAck ack = simulateInbound(channel, inbound).block(Duration.ofSeconds(2));

        assertThat(ack).isNotNull();
        assertThat(ack.success()).isTrue();
        assertThat(ack.remoteId()).isEqualTo("remote-" + inbound.id());
        assertThat(received).containsExactly(inbound);

        channel.stop().block(Duration.ofSeconds(2));
    }

    // ------------------------------------------------------------------ scenario 2

    @Test
    void outboundFailure_isSurfacedAs_sendFailed() {
        Channel channel = newChannel();
        channel.start(anyHandler()).block(Duration.ofSeconds(2));

        ChannelOutboundSender failing = newFailingSender(channel);
        ChannelMessage outbound =
                ChannelMessage.of(ChannelIdentity.of(channel.id(), "peer-1"), "reply");

        ChannelAck ack = failing.send(outbound).block(Duration.ofSeconds(2));

        assertThat(ack).isNotNull();
        assertThat(ack.success()).isFalse();
        assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.SEND_FAILED);

        channel.stop().block(Duration.ofSeconds(2));
    }

    // ------------------------------------------------------------------ scenario 3

    @Test
    void concurrentInbound_preservesOrdering_perDestination() throws InterruptedException {
        Channel channel = newChannel();
        int perDestination = 32;
        List<ChannelMessage> observed = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(perDestination * 2);
        ChannelInboundHandler handler =
                message -> {
                    observed.add(message);
                    latch.countDown();
                    return Mono.just(ChannelAck.ok());
                };
        channel.start(handler).block(Duration.ofSeconds(2));

        ChannelIdentity a = ChannelIdentity.of(channel.id(), "dest-A");
        ChannelIdentity b = ChannelIdentity.of(channel.id(), "dest-B");
        List<ChannelMessage> seqA = buildSequence(a, perDestination);
        List<ChannelMessage> seqB = buildSequence(b, perDestination);

        // Interleave the two destinations on the submission side; within each destination we
        // submit serially so the adapter's per-destination ordering contract can be verified.
        Flux.fromIterable(seqA).flatMap(m -> simulateInbound(channel, m), 1).subscribe();
        Flux.fromIterable(seqB).flatMap(m -> simulateInbound(channel, m), 1).subscribe();

        assertThat(latch.await(5, TimeUnit.SECONDS))
                .as("expected all %d messages to be delivered", perDestination * 2)
                .isTrue();

        assertThat(extractDestination(observed, "dest-A")).containsExactlyElementsOf(seqA);
        assertThat(extractDestination(observed, "dest-B")).containsExactlyElementsOf(seqB);

        channel.stop().block(Duration.ofSeconds(2));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Drive an inbound message through the channel's registered handler. The default implementation
     * targets the bundled {@link io.kairo.channel.LoopbackChannel} — adapter authors that extend
     * this TCK for a real transport override this hook.
     */
    protected Mono<ChannelAck> simulateInbound(Channel channel, ChannelMessage message) {
        if (channel instanceof io.kairo.channel.LoopbackChannel loopback) {
            return loopback.simulateInbound(message);
        }
        throw new UnsupportedOperationException(
                "override ChannelTCK#simulateInbound for " + channel.getClass().getName());
    }

    private static ChannelInboundHandler anyHandler() {
        return message -> Mono.just(ChannelAck.ok());
    }

    private static List<ChannelMessage> buildSequence(ChannelIdentity identity, int n) {
        List<ChannelMessage> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(ChannelMessage.of(identity, "msg-" + i));
        }
        return out;
    }

    private static List<ChannelMessage> extractDestination(
            List<ChannelMessage> all, String destination) {
        return all.stream().filter(m -> destination.equals(m.identity().destination())).toList();
    }
}
