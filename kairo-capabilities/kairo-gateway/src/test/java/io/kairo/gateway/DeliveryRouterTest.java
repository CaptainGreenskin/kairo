/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;
import io.kairo.gateway.mirror.MirrorStore;
import io.kairo.gateway.routing.DeliveryRouter;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveryRouterTest {

    @Test
    void routesToRegisteredAdapter() {
        var adapter = new FakeAdapter("telegram");
        var router = new DeliveryRouter(Map.of("telegram", adapter), MirrorStore.noop());
        var results = router.route(DeliveryTarget.chat("telegram", "c-1"), "hi", Map.of()).block();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        assertThat(adapter.sends).containsExactly("hi");
    }

    @Test
    void localTargetSkipsAdapters() {
        var router = new DeliveryRouter(Map.of(), MirrorStore.noop());
        var results = router.route(DeliveryTarget.local(), "stored", Map.of()).block();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
    }

    @Test
    void unknownChannelReturnsPermanentFailure() {
        var router = new DeliveryRouter(Map.of(), MirrorStore.noop());
        var results = router.route(DeliveryTarget.channel("nowhere"), "x", Map.of()).block();
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).failureMode()).isEqualTo(SendResult.FailureMode.PERMANENT);
        assertThat(results.get(0).errorMessage()).contains("nowhere");
    }

    @Test
    void nullTargetIsHandledGracefully() {
        var router = new DeliveryRouter(Map.of(), MirrorStore.noop());
        var results = router.route(null, "x", Map.of()).block();
        assertThat(results.get(0).success()).isFalse();
    }

    @Test
    void adapterErrorMapsToTransientFailure() {
        var adapter = new FakeAdapter("x");
        adapter.forceFailure = SendResult.FailureMode.RATE_LIMITED;
        Map<String, Channel> m = Map.of("x", adapter);
        var router = new DeliveryRouter(m, MirrorStore.noop());
        var results = router.route(DeliveryTarget.channel("x"), "y", null).block();
        assertThat(results.get(0).success()).isFalse();
        assertThat(results.get(0).failureMode()).isEqualTo(SendResult.FailureMode.RATE_LIMITED);
    }

    @Test
    void replyToMessageIdPassesThroughMetadata() {
        var adapter = new FakeAdapter("x");
        Map<String, Channel> m = Map.of("x", adapter);
        var router = new DeliveryRouter(m, MirrorStore.noop());
        var results =
                router.route(
                                DeliveryTarget.channel("x"),
                                "reply",
                                Map.of("replyToMessageId", "msg-123"))
                        .block();
        assertThat(results.get(0).success()).isTrue();
    }
}
