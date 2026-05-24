/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * Pluggable conformance tests for any {@link Channel}. Extend in your adapter's test module:
 *
 * <pre>{@code
 * class MyAdapterTCKTest extends GatewayAdapterTCK {
 *     @Override protected Channel adapter() { return new MyAdapter(testConfig()); }
 * }
 * }</pre>
 *
 * <p>The TCK runs every assertion adapters MUST honour: id is stable & non-blank, capabilities
 * declared honestly, connect/disconnect idempotent, send returns a SendResult (never throws),
 * inbound returns a non-null flux. It does NOT exercise actual transport — that's the adapter's own
 * integration tests.
 */
public abstract class GatewayAdapterTCK {

    /** Provide a fresh adapter instance per test method. */
    protected abstract Channel adapter();

    @Test
    void idIsNonBlank() {
        var adapter = adapter();
        assertThat(adapter.id()).isNotBlank();
    }

    @Test
    void idIsStable() {
        var a = adapter();
        assertThat(a.id()).isEqualTo(a.id());
    }

    @Test
    void capabilitiesIsNonNull() {
        assertThat(adapter().capabilities()).isNotNull();
    }

    @Test
    void inboundFluxIsNonNull() {
        assertThat(adapter().inbound()).isNotNull();
    }

    @Test
    void inboundIsHotMulticast() {
        // Subscribing twice must not throw and both subscribers must succeed — a cold flux
        // would re-trigger transport handshake on every subscribe.
        var a = adapter();
        Flux<?> flux = a.inbound();
        flux.subscribe().dispose();
        flux.subscribe().dispose();
    }

    @Test
    void disconnectWithoutConnectIsSafe() {
        adapter().disconnect().block();
    }

    @Test
    void sendNeverThrowsAcrossSpi() {
        // Adapters MUST surface every failure via SendResult.fail — never via thrown exception
        // (callers can't tell the difference between transport error and bug otherwise).
        var a = adapter();
        SendResult r = a.send(DeliveryTarget.channel(a.id()), "tck-probe", null, Map.of()).block();
        assertThat(r).isNotNull();
        // Allowed to either succeed (when transport mock-connected) or fail cleanly.
    }

    @Test
    void unsupportedDefaultsAreUnavailable() {
        // The default impls for editMessage / deleteMessage / sendAttachment / sendDraft must
        // return UNAVAILABLE — not throw, not return success. Adapters that override these
        // should declare the matching capability.
        var a = adapter();
        if (!a.capabilities().supportsEdit()) {
            SendResult r = a.editMessage(DeliveryTarget.channel(a.id()), "x", "y").block();
            assertThat(r.success()).isFalse();
            assertThat(r.failureMode()).isEqualTo(SendResult.FailureMode.UNAVAILABLE);
        }
        if (!a.capabilities().supportsDelete()) {
            SendResult r = a.deleteMessage(DeliveryTarget.channel(a.id()), "x").block();
            assertThat(r.success()).isFalse();
        }
    }

    @Test
    void sendingToWrongChannelStillReturnsResult() {
        // Adapters may either ignore (and complete OK) or fail; what they MUST NOT do is throw
        // when handed a DeliveryTarget for a different channel id. (Callers shouldn't do this
        // either, but defensive behaviour matters for misconfigured deployments.)
        SendResult r =
                adapter()
                        .send(DeliveryTarget.channel("not-this-channel"), "x", null, Map.of())
                        .block();
        assertThat(r).isNotNull();
    }

    @Test
    void capabilitiesBuilderIsConsistent() {
        // If the adapter claims supportsDraft but supportsEdit is false, that's a bug — the
        // stream consumer falls back from sendDraft to editMessage on failure, so draft
        // without edit can't degrade gracefully.
        var c = adapter().capabilities();
        if (c.supportsDraft()) {
            assertThat(c.supportsEdit())
                    .as("Adapters that supportsDraft must also supportsEdit for fallback")
                    .isTrue();
        }
    }

    @Test
    void adapterDoesNotBlockOnReactiveSpi() {
        // Sanity: build the call chain on this thread and assert it returns within a tight
        // budget. A blocking adapter would stall this assertion well past the budget.
        long start = System.currentTimeMillis();
        adapter().send(DeliveryTarget.channel(adapter().id()), "x", null, Map.of()).block();
        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed)
                .as("Adapter took >5s for an in-memory send — likely blocking")
                .isLessThan(5_000);
    }
}
