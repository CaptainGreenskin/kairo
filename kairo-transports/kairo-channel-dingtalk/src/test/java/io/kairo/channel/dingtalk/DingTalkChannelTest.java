/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.channel.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.MessageType;
import io.kairo.api.gateway.SendResult;
import io.kairo.api.gateway.SessionSource;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class DingTalkChannelTest {

    private DingTalkChannel buildChannel() {
        // Real outbound client pointed at loopback; tests don't actually send.
        DingTalkOutboundClient client =
                new DingTalkOutboundClient(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                        new ObjectMapper(),
                        "http://127.0.0.1:1/nope",
                        null,
                        Duration.ofSeconds(1));
        return new DingTalkChannel(
                "dingtalk", client, new DingTalkMessageMapper("dingtalk"), List.of());
    }

    private ChannelMessage testMessage(String msgId) {
        return new ChannelMessage(
                "id-" + msgId,
                SessionSource.of("dingtalk", "c", "u"),
                MessageType.TEXT,
                "hello",
                List.of(),
                msgId,
                null,
                null,
                Instant.now(),
                Map.of(DingTalkMessageMapper.ATTR_MSG_ID, msgId));
    }

    @Test
    void connectDisconnectFlipsRunningState() {
        DingTalkChannel ch = buildChannel();
        assertThat(ch.isRunning()).isFalse();
        ch.connect().block();
        assertThat(ch.isRunning()).isTrue();
        ch.disconnect().block();
        assertThat(ch.isRunning()).isFalse();
    }

    @Test
    void inboundEmitsDispatched() throws Exception {
        DingTalkChannel ch = buildChannel();
        ch.connect().block();
        List<ChannelMessage> received = new CopyOnWriteArrayList<>();
        Disposable sub = ch.inbound().subscribe(received::add);
        ch.dispatchInbound(testMessage("msg-1"));
        Thread.sleep(30);
        assertThat(received).hasSize(1);
        sub.dispose();
        ch.disconnect().block();
    }

    @Test
    void duplicateMsgIdDropped() throws Exception {
        DingTalkChannel ch = buildChannel();
        ch.connect().block();
        List<ChannelMessage> received = new CopyOnWriteArrayList<>();
        Disposable sub = ch.inbound().subscribe(received::add);
        ChannelMessage msg = testMessage("dup");
        ch.dispatchInbound(msg);
        ch.dispatchInbound(msg);
        Thread.sleep(30);
        assertThat(received).hasSize(1);
        assertThat(ch.dedupSetSize()).isEqualTo(1);
        sub.dispose();
        ch.disconnect().block();
    }

    @Test
    void sendBeforeConnectFails() {
        DingTalkChannel ch = buildChannel();
        SendResult r = ch.send(DeliveryTarget.chat("dingtalk", "c"), "x", null, Map.of()).block();
        assertThat(r.success()).isFalse();
        assertThat(r.failureMode()).isEqualTo(SendResult.FailureMode.UNAVAILABLE);
    }

    @Test
    void disconnectClearsDedup() {
        DingTalkChannel ch = buildChannel();
        ch.connect().block();
        ch.dispatchInbound(testMessage("m"));
        assertThat(ch.dedupSetSize()).isEqualTo(1);
        ch.disconnect().block();
        assertThat(ch.dedupSetSize()).isZero();
    }

    @Test
    void inboundBeforeConnectIsDropped() throws Exception {
        DingTalkChannel ch = buildChannel();
        List<ChannelMessage> received = new CopyOnWriteArrayList<>();
        Disposable sub = ch.inbound().subscribe(received::add);
        ch.dispatchInbound(testMessage("early"));
        Thread.sleep(30);
        assertThat(received).isEmpty();
        sub.dispose();
    }

    @Test
    void capabilitiesAreTextOnly() {
        DingTalkChannel ch = buildChannel();
        var caps = ch.capabilities();
        assertThat(caps.supportsEdit()).isFalse();
        assertThat(caps.supportsDraft()).isFalse();
        assertThat(caps.supportsImage()).isFalse();
    }
}
