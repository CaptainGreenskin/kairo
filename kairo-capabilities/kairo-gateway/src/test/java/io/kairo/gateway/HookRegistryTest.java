/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;
import io.kairo.api.gateway.SessionSource;
import io.kairo.gateway.hooks.GatewayHookRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HookRegistryTest {

    @Test
    void inboundHooksRunInOrder() {
        var r = new GatewayHookRegistry();
        r.onInbound(msg -> ChannelMessage.text(msg.source(), msg.text() + "-a"));
        r.onInbound(msg -> ChannelMessage.text(msg.source(), msg.text() + "-b"));
        var result = r.fireInbound(ChannelMessage.text(SessionSource.of("x", "y", "z"), "start"));
        assertThat(result.text()).isEqualTo("start-a-b");
    }

    @Test
    void inboundHookVeto() {
        var r = new GatewayHookRegistry();
        r.onInbound(msg -> null);
        var result = r.fireInbound(ChannelMessage.text(SessionSource.of("x", "y", "z"), "x"));
        assertThat(result).isNull();
    }

    @Test
    void hookThatThrowsDoesNotPropagate() {
        var r = new GatewayHookRegistry();
        r.onInbound(
                msg -> {
                    throw new RuntimeException("boom");
                });
        var result = r.fireInbound(ChannelMessage.text(SessionSource.of("x", "y", "z"), "x"));
        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("x");
    }

    @Test
    void outboundHooksAccumulate() {
        var r = new GatewayHookRegistry();
        r.onOutbound((t, c) -> "[" + c + "]");
        r.onOutbound((t, c) -> c.toUpperCase());
        String result = r.fireOutbound(DeliveryTarget.local(), "hi");
        assertThat(result).isEqualTo("[HI]");
    }

    @Test
    void resultHookFires() {
        var r = new GatewayHookRegistry();
        var count = new AtomicInteger();
        r.onResult((t, c, res) -> count.incrementAndGet());
        r.fireResult(DeliveryTarget.local(), "x", SendResult.ok("m"));
        assertThat(count.get()).isEqualTo(1);
    }
}
