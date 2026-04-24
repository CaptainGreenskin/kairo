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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelFailureMode;
import io.kairo.api.channel.ChannelIdentity;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.api.channel.ChannelMessage;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Three DingTalk-specific scenarios on top of the baseline {@link io.kairo.channel.tck.ChannelTCK}:
 *
 * <ol>
 *   <li>Duplicate webhook delivery with the same msgId is deduplicated (handler sees it once).
 *   <li>Signature mismatch at the verifier layer is rejected without invoking the handler.
 *   <li>Outbound HTTP 429 is surfaced as {@link ChannelFailureMode#RATE_LIMITED}.
 * </ol>
 */
class DingTalkChannelExtendedScenariosTest {

    private MockWebServer dingTalkServer;

    @BeforeEach
    void setUp() throws Exception {
        dingTalkServer = new MockWebServer();
        dingTalkServer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        dingTalkServer.shutdown();
    }

    @Test
    void duplicateMsgId_isDeduplicated() {
        AtomicInteger handlerCalls = new AtomicInteger();
        List<ChannelMessage> received = new CopyOnWriteArrayList<>();
        ChannelInboundHandler handler =
                message -> {
                    handlerCalls.incrementAndGet();
                    received.add(message);
                    return Mono.just(ChannelAck.ok("remote-" + message.id()));
                };

        DingTalkChannel channel = buildChannel();
        channel.start(handler).block(Duration.ofSeconds(2));

        String raw =
                """
                {
                  "msgtype": "text",
                  "msgId": "duplicate-msg-42",
                  "conversationId": "conv-dedup",
                  "senderId": "sender-dedup",
                  "text": { "content": "same message twice" }
                }
                """;

        ChannelAck first = channel.dispatchInbound(raw).block(Duration.ofSeconds(2));
        ChannelAck second = channel.dispatchInbound(raw).block(Duration.ofSeconds(2));

        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(received).hasSize(1);
        assertThat(first).isNotNull();
        assertThat(first.success()).isTrue();
        assertThat(second).isNotNull();
        assertThat(second.success()).as("dedup replay surfaces a synthetic ok").isTrue();

        channel.stop().block(Duration.ofSeconds(2));
    }

    @Test
    void signatureMismatch_isRejectedByVerifier_withoutInvokingHandler() {
        DingTalkSignatureVerifier v = new DingTalkSignatureVerifier("real-secret");
        long now = System.currentTimeMillis();
        String tamperedSignature = v.sign(now).substring(0, 10) + "AAAAAAAAAAAAAAAAAAAAAA==";

        AtomicInteger handlerCalls = new AtomicInteger();
        ChannelInboundHandler handler =
                message -> {
                    handlerCalls.incrementAndGet();
                    return Mono.just(ChannelAck.ok());
                };
        DingTalkChannel channel = buildChannel();
        channel.start(handler).block(Duration.ofSeconds(2));

        // Simulates what the webhook controller does: verify first, dispatch only on success.
        boolean ok = v.verify(now, tamperedSignature);
        if (ok) {
            channel.dispatchInbound(
                            new ChannelMessage(
                                    "msg-1",
                                    new ChannelIdentity(
                                            "dingtalk-test", "conv-1", java.util.Map.of()),
                                    "should not reach handler",
                                    java.time.Instant.now(),
                                    java.util.Map.of()))
                    .block();
        }

        assertThat(ok).isFalse();
        assertThat(handlerCalls.get()).isZero();

        channel.stop().block(Duration.ofSeconds(2));
    }

    @Test
    void outboundHttp429_isSurfacedAsRateLimited() {
        dingTalkServer.enqueue(new MockResponse().setResponseCode(429).setBody(""));

        DingTalkChannel channel = buildChannelWith(dingTalkServer.url("/robot/send").toString());
        channel.start(m -> Mono.just(ChannelAck.ok())).block(Duration.ofSeconds(2));

        ChannelMessage reply =
                ChannelMessage.of(ChannelIdentity.of("dingtalk-test", "conv-1"), "reply");
        ChannelAck ack = channel.sender().send(reply).block(Duration.ofSeconds(5));

        assertThat(ack).isNotNull();
        assertThat(ack.success()).isFalse();
        assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.RATE_LIMITED);

        channel.stop().block(Duration.ofSeconds(2));
    }

    @Test
    void outboundDingTalkErrcode130101_isSurfacedAsRateLimited() {
        dingTalkServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setBody("{\"errcode\":130101,\"errmsg\":\"send too fast, retry later\"}"));

        DingTalkChannel channel = buildChannelWith(dingTalkServer.url("/robot/send").toString());
        channel.start(m -> Mono.just(ChannelAck.ok())).block(Duration.ofSeconds(2));

        ChannelMessage reply =
                ChannelMessage.of(ChannelIdentity.of("dingtalk-test", "conv-1"), "reply");
        ChannelAck ack = channel.sender().send(reply).block(Duration.ofSeconds(5));

        assertThat(ack).isNotNull();
        assertThat(ack.success()).isFalse();
        assertThat(ack.failureMode()).isEqualTo(ChannelFailureMode.RATE_LIMITED);

        channel.stop().block(Duration.ofSeconds(2));
    }

    private DingTalkChannel buildChannel() {
        return buildChannelWith("https://oapi.dingtalk.com/robot/send?access_token=stub");
    }

    private DingTalkChannel buildChannelWith(String webhookUrl) {
        DingTalkSignatureVerifier signer = new DingTalkSignatureVerifier("stub-secret");
        ObjectMapper objectMapper = new ObjectMapper();
        DingTalkOutboundClient outbound =
                new DingTalkOutboundClient(
                        HttpClient.newHttpClient(),
                        objectMapper,
                        webhookUrl,
                        null, // outbound signing is optional; unsigned for the mock-server tests
                        Duration.ofSeconds(5));
        DingTalkMessageMapper mapper = new DingTalkMessageMapper("dingtalk-test", objectMapper);
        return new DingTalkChannel("dingtalk-test", outbound, mapper, List.of());
    }
}
