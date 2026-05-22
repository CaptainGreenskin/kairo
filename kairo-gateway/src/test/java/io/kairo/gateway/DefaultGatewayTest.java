/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SessionSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class DefaultGatewayTest {

    @Test
    void startConnectsEveryAdapter() {
        var a = new FakeAdapter("a");
        var b = new FakeAdapter("b");
        var gw = new DefaultGateway(List.of(a, b), null, null);
        gw.start().block();
        assertThat(a.connected()).isTrue();
        assertThat(b.connected()).isTrue();
        gw.stop().block();
        assertThat(a.connected()).isFalse();
        assertThat(b.connected()).isFalse();
    }

    @Test
    void duplicateAdapterIdRejected() {
        var a = new FakeAdapter("dup");
        var b = new FakeAdapter("dup");
        assertThatThrownBy(() -> new DefaultGateway(List.of(a, b), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void doubleStartFails() {
        var gw = new DefaultGateway(List.of(new FakeAdapter("a")), null, null);
        gw.start().block();
        assertThatThrownBy(() -> gw.start().block()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deliverBeforeStartFails() {
        var gw = new DefaultGateway(List.of(new FakeAdapter("a")), null, null);
        assertThatThrownBy(() -> gw.deliver(DeliveryTarget.channel("a"), "hi", Map.of()).block())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inboundFanInWorks() throws Exception {
        var a = new FakeAdapter("a");
        var b = new FakeAdapter("b");
        var gw = new DefaultGateway(List.of(a, b), null, null);
        gw.start().block();
        List<ChannelMessage> received = new CopyOnWriteArrayList<>();
        Disposable sub = gw.inbound().subscribe(received::add);
        a.emit(ChannelMessage.text(SessionSource.of("a", "c1", "u"), "from-a"));
        b.emit(ChannelMessage.text(SessionSource.of("b", "c2", "u"), "from-b"));
        // Give the multicast a tick to fan out.
        Thread.sleep(50);
        assertThat(received).extracting(ChannelMessage::text).contains("from-a", "from-b");
        sub.dispose();
        gw.stop().block();
    }

    @Test
    void sessionsRecordOnInbound() throws Exception {
        var a = new FakeAdapter("a");
        var gw = new DefaultGateway(List.of(a), null, null);
        gw.start().block();
        // Subscribe so the source flux is pulled.
        Disposable sub = gw.inbound().subscribe();
        a.emit(ChannelMessage.text(SessionSource.of("a", "c-1", "u"), "hi"));
        Thread.sleep(30);
        assertThat(gw.sessions().size()).isEqualTo(1);
        sub.dispose();
        gw.stop().block();
    }

    @Test
    void deliverRoutesToAdapter() {
        var a = new FakeAdapter("telegram");
        var gw = new DefaultGateway(List.of(a), null, null);
        gw.start().block();
        var results = gw.deliver(DeliveryTarget.chat("telegram", "c-1"), "hello", Map.of()).block();
        assertThat(results.get(0).success()).isTrue();
        assertThat(a.sends).containsExactly("hello");
        gw.stop().block();
    }

    @Test
    void deliverParseConvenience() {
        var a = new FakeAdapter("telegram");
        var gw = new DefaultGateway(List.of(a), null, null);
        gw.start().block();
        var results = gw.deliver("telegram:c-1", "hi").block();
        assertThat(results.get(0).success()).isTrue();
        gw.stop().block();
    }

    @Test
    void stopIsIdempotent() {
        var gw = new DefaultGateway(List.of(new FakeAdapter("a")), null, null);
        gw.start().block();
        gw.stop().block();
        gw.stop().block(); // must not throw
    }
}
