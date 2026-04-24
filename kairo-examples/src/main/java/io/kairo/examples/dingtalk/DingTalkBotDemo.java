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
package io.kairo.examples.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelIdentity;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.api.channel.ChannelMessage;
import io.kairo.channel.dingtalk.DingTalkChannel;
import io.kairo.channel.dingtalk.DingTalkMessageMapper;
import io.kairo.channel.dingtalk.DingTalkOutboundClient;
import io.kairo.channel.dingtalk.DingTalkSignatureVerifier;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Demonstrates a DingTalk custom-bot round-trip in ~40 lines.
 *
 * <p>Required env vars (obtained when you register the bot in the DingTalk admin console):
 *
 * <ul>
 *   <li>{@code DINGTALK_WEBHOOK_URL} — e.g. {@code
 *       https://oapi.dingtalk.com/robot/send?access_token=...}
 *   <li>{@code DINGTALK_SIGNING_SECRET} — HMAC-SHA256 shared secret
 * </ul>
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>The {@code handler} receives a {@link ChannelMessage} (e.g., {@code @bot "summarize recent
 *       PRs"}).
 *   <li>Your agent logic produces a reply string.
 *   <li>The channel's {@code sender()} POSTs the reply back to the thread via the bot webhook.
 * </ol>
 *
 * <p>In a real deployment, wire this via the Spring Boot starter ({@code
 * kairo-spring-boot-starter-channel-dingtalk}) and serve the controller at {@code POST
 * /kairo/channel/dingtalk/callback}. This demo shows the underlying programmatic surface.
 *
 * @since v0.9.1
 */
public final class DingTalkBotDemo {

    private DingTalkBotDemo() {}

    public static void main(String[] args) {
        String webhookUrl = envOrDefault("DINGTALK_WEBHOOK_URL", "<set DINGTALK_WEBHOOK_URL>");
        String signingSecret =
                envOrDefault("DINGTALK_SIGNING_SECRET", "<set DINGTALK_SIGNING_SECRET>");

        ObjectMapper objectMapper = new ObjectMapper();
        DingTalkSignatureVerifier signer = new DingTalkSignatureVerifier(signingSecret);
        DingTalkOutboundClient outbound =
                new DingTalkOutboundClient(
                        HttpClient.newHttpClient(),
                        objectMapper,
                        webhookUrl,
                        signer,
                        Duration.ofSeconds(5));
        DingTalkMessageMapper mapper = new DingTalkMessageMapper("dingtalk-demo", objectMapper);
        DingTalkChannel channel = new DingTalkChannel("dingtalk-demo", outbound, mapper, List.of());

        ChannelInboundHandler handler =
                message -> {
                    System.out.println(
                            "[inbound] "
                                    + message.identity().destination()
                                    + " -> "
                                    + message.content());
                    String reply = respond(message.content());
                    ChannelMessage back =
                            ChannelMessage.of(
                                    ChannelIdentity.of(
                                            "dingtalk-demo", message.identity().destination()),
                                    reply);
                    return channel.sender()
                            .send(back)
                            .doOnNext(ack -> System.out.println("[outbound] " + ack))
                            .map(ack -> ack.success() ? ChannelAck.ok() : ack);
                };

        channel.start(handler).block();

        // Simulate an inbound delivery; in production, DingTalk POSTs this JSON to the webhook
        // controller, which calls channel.dispatchInbound(rawJson) after signature verification.
        String rawPayload =
                """
                {
                  "msgtype": "text",
                  "msgId": "demo-001",
                  "conversationId": "conv-demo",
                  "senderId": "sender-demo",
                  "senderNick": "demo-user",
                  "text": { "content": "summarize recent PRs" }
                }
                """;
        ChannelAck ack = channel.dispatchInbound(rawPayload).block();
        System.out.println("[dispatchInbound] " + ack);

        channel.stop().block();
    }

    private static String respond(String input) {
        return "You said: " + input + " — (your agent output goes here)";
    }

    private static String envOrDefault(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
