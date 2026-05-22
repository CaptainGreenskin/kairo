/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;
import io.kairo.api.gateway.SessionSource;
import io.kairo.gateway.mirror.JsonlMirrorStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlMirrorStoreTest {

    @Test
    void recordsInboundAndOutbound(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("mirror.ndjson");
        var store = new JsonlMirrorStore(file);

        store.recordInbound(ChannelMessage.text(SessionSource.of("x", "y", "z"), "hi"));
        store.recordOutbound(DeliveryTarget.chat("x", "y"), "reply", SendResult.ok("m-1"));
        store.recordOutboundLocal("local-only");

        var lines = Files.readAllLines(file);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("\"direction\":\"in\"").contains("\"text\":\"hi\"");
        assertThat(lines.get(1)).contains("\"direction\":\"out\"").contains("\"ok\":true");
        assertThat(lines.get(2)).contains("\"channelId\":\"local\"");
    }

    @Test
    void failureModeAndErrorSurfaceInOutboundRow(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("mirror.ndjson");
        var store = new JsonlMirrorStore(file);
        store.recordOutbound(
                DeliveryTarget.chat("x", "y"),
                "msg",
                SendResult.fail(SendResult.FailureMode.RATE_LIMITED, "429"));
        var line = Files.readAllLines(file).get(0);
        assertThat(line).contains("\"ok\":false");
        assertThat(line).contains("\"failureMode\":\"RATE_LIMITED\"");
        assertThat(line).contains("\"error\":\"429\"");
    }

    @Test
    void noopMirrorIsSafe() {
        var store = io.kairo.gateway.mirror.MirrorStore.noop();
        // Just verify the calls don't throw.
        store.recordInbound(ChannelMessage.text(SessionSource.of("x", "y", "z"), "hi"));
        store.recordOutbound(DeliveryTarget.chat("x", "y"), "x", SendResult.ok("m"));
        store.recordOutboundLocal("hi");
    }
}
