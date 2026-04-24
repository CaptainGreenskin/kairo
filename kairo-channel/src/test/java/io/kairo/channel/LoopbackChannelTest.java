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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelFailureMode;
import io.kairo.api.channel.ChannelIdentity;
import io.kairo.api.channel.ChannelMessage;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class LoopbackChannelTest {

    @Test
    void outboundBeforeStart_returnsSendFailed() {
        LoopbackChannel channel = new LoopbackChannel("lb");

        ChannelAck ack =
                channel.sender()
                        .send(ChannelMessage.of(ChannelIdentity.of("lb", "peer"), "hi"))
                        .block(Duration.ofSeconds(1));

        assertThat(ack).isNotNull();
        assertThat(ack.success()).isFalse();
        assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.SEND_FAILED);
        assertThat(channel.outboundLog()).isEmpty();
    }

    @Test
    void outboundAfterStart_isCaptured() {
        LoopbackChannel channel = new LoopbackChannel("lb");
        channel.start(message -> Mono.just(ChannelAck.ok())).block(Duration.ofSeconds(1));

        ChannelMessage msg = ChannelMessage.of(ChannelIdentity.of("lb", "peer"), "hi");
        ChannelAck ack = channel.sender().send(msg).block(Duration.ofSeconds(1));

        assertThat(ack).isNotNull();
        assertThat(ack.success()).isTrue();
        assertThat(ack.remoteId()).isEqualTo(msg.id());
        assertThat(channel.outboundLog()).containsExactly(msg);
    }

    @Test
    void simulateInbound_invokesHandler() {
        LoopbackChannel channel = new LoopbackChannel("lb");
        AtomicReference<ChannelMessage> captured = new AtomicReference<>();
        channel.start(
                        message -> {
                            captured.set(message);
                            return Mono.just(ChannelAck.ok("ack-" + message.id()));
                        })
                .block(Duration.ofSeconds(1));

        ChannelMessage msg = ChannelMessage.of(ChannelIdentity.of("lb", "peer"), "hi");
        ChannelAck ack = channel.simulateInbound(msg).block(Duration.ofSeconds(1));

        assertThat(captured.get()).isEqualTo(msg);
        assertThat(ack).isNotNull();
        assertThat(ack.success()).isTrue();
        assertThat(ack.remoteId()).isEqualTo("ack-" + msg.id());
    }

    @Test
    void simulateInboundWithoutStart_returnsRejected() {
        LoopbackChannel channel = new LoopbackChannel("lb");

        ChannelAck ack =
                channel.simulateInbound(ChannelMessage.of(ChannelIdentity.of("lb", "peer"), "hi"))
                        .block(Duration.ofSeconds(1));

        assertThat(ack).isNotNull();
        assertThat(ack.success()).isFalse();
        assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.REJECTED);
    }

    @Test
    void doubleStart_throws() {
        LoopbackChannel channel = new LoopbackChannel("lb");
        channel.start(m -> Mono.just(ChannelAck.ok())).block(Duration.ofSeconds(1));

        assertThatThrownBy(
                        () ->
                                channel.start(m -> Mono.just(ChannelAck.ok()))
                                        .block(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already started");
    }

    @Test
    void stopClearsHandler_andStopsAcceptingOutbound() {
        LoopbackChannel channel = new LoopbackChannel("lb");
        channel.start(m -> Mono.just(ChannelAck.ok())).block(Duration.ofSeconds(1));
        channel.stop().block(Duration.ofSeconds(1));

        assertThat(channel.isRunning()).isFalse();

        ChannelAck ack =
                channel.sender()
                        .send(ChannelMessage.of(ChannelIdentity.of("lb", "peer"), "post-stop"))
                        .block(Duration.ofSeconds(1));
        assertThat(ack).isNotNull();
        assertThat(ack.success()).isFalse();
    }

    @Test
    void clearOutboundLog_wipesCapturedMessages() {
        LoopbackChannel channel = new LoopbackChannel("lb");
        channel.start(m -> Mono.just(ChannelAck.ok())).block(Duration.ofSeconds(1));
        channel.sender()
                .send(ChannelMessage.of(ChannelIdentity.of("lb", "peer"), "x"))
                .block(Duration.ofSeconds(1));
        assertThat(channel.outboundLog()).hasSize(1);

        channel.clearOutboundLog();

        assertThat(channel.outboundLog()).isEmpty();
    }
}
