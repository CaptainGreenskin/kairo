/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.PlatformCapabilities;
import io.kairo.api.gateway.SendResult;
import io.kairo.gateway.stream.StreamConsumer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class StreamConsumerTest {

    @Test
    void sendsInitialThenFinalEditForChunkedStream() {
        var adapter = new FakeAdapter("x", PlatformCapabilities.builder().edit().build());
        // Use short interval so the consumer flushes between deltas in this test.
        var consumer = new StreamConsumer(Duration.ofMillis(1));
        SendResult result =
                consumer.consume(
                                adapter,
                                DeliveryTarget.chat("x", "c-1"),
                                Flux.just("Hello", " ", "world", "."))
                        .block();
        assertThat(result.success()).isTrue();
        // At least one send happened and the final text was edited in.
        assertThat(adapter.sends).isNotEmpty();
        assertThat(adapter.edits).isNotEmpty();
        assertThat(adapter.edits.get(adapter.edits.size() - 1)).isEqualTo("Hello world.");
    }

    @Test
    void emptyStreamStillProducesResult() {
        var adapter = new FakeAdapter("x");
        var consumer = new StreamConsumer();
        SendResult result =
                consumer.consume(adapter, DeliveryTarget.chat("x", "c-1"), Flux.empty()).block();
        assertThat(result.success()).isTrue();
        // Empty stream — final defer sends once with empty text.
        assertThat(adapter.sends).hasSize(1);
        assertThat(adapter.sends.get(0)).isEmpty();
    }

    @Test
    void noEditCapabilityStillCompletes() {
        var adapter = new FakeAdapter("x", PlatformCapabilities.textOnly());
        var consumer = new StreamConsumer(Duration.ofMillis(1));
        SendResult result =
                consumer.consume(adapter, DeliveryTarget.chat("x", "c-1"), Flux.just("a", "b"))
                        .block();
        assertThat(result.success()).isTrue();
        // No edit support — only one send (the first delta), subsequent intermediate updates
        // can't edit so they no-op. Final defer also has no edit to do, so total sends stays 1.
        assertThat(adapter.sends).hasSize(1);
        assertThat(adapter.edits).isEmpty();
    }

    @Test
    void draftCapabilityUsesSendDraft() {
        var adapter = new FakeAdapter("x", PlatformCapabilities.builder().draft().edit().build());
        var consumer = new StreamConsumer(Duration.ofMillis(1));
        consumer.consume(
                        adapter,
                        DeliveryTarget.chat("x", "c-1"),
                        Flux.just("first", " ", "second", " ", "third."))
                .block();
        // First emission goes through send (to mint message id); subsequent intermediate updates
        // route through sendDraft.
        assertThat(adapter.drafts.get()).isGreaterThanOrEqualTo(1);
    }
}
