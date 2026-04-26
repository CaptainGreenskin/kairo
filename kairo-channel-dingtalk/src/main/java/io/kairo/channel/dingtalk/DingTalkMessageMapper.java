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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.Experimental;
import io.kairo.api.channel.ChannelIdentity;
import io.kairo.api.channel.ChannelMessage;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps between DingTalk custom-bot webhook JSON and {@link ChannelMessage}. Text content lands in
 * {@link ChannelMessage#content()}; routing metadata (conversation id, sender id, message id) is
 * preserved under {@link ChannelIdentity#attributes()} and {@link ChannelMessage#attributes()} so
 * round-tripping stays lossless.
 *
 * <p>Only the {@code text} message type is mapped verbatim — unsupported types round-trip an empty
 * {@code content} string but preserve their raw JSON under attribute {@code rawMsgType}.
 *
 * @since v0.9.1 (Experimental)
 */
@Experimental("DingTalk message mapper — contract may change in v0.10")
public final class DingTalkMessageMapper {

    /** Attribute key for the adapter-supplied DingTalk message id (used for idempotency). */
    public static final String ATTR_MSG_ID = "dingtalk.msgId";

    /** Attribute key for the raw DingTalk message type, e.g. {@code "text"}. */
    public static final String ATTR_MSG_TYPE = "dingtalk.msgType";

    /** Attribute key for the DingTalk conversation id (group chat or 1:1). */
    public static final String ATTR_CONVERSATION_ID = "dingtalk.conversationId";

    /** Attribute key for the sender's DingTalk userId. */
    public static final String ATTR_SENDER_ID = "dingtalk.senderId";

    /** Attribute key for the sender's display nickname. */
    public static final String ATTR_SENDER_NICK = "dingtalk.senderNick";

    /** Attribute key for the webhook-provided session token (used when replying via API). */
    public static final String ATTR_SESSION_WEBHOOK = "dingtalk.sessionWebhook";

    private final String channelId;
    private final ObjectMapper objectMapper;

    public DingTalkMessageMapper(String channelId, ObjectMapper objectMapper) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        this.channelId = channelId;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /** Convenience overload that uses a fresh {@link ObjectMapper}. */
    public DingTalkMessageMapper(String channelId) {
        this(channelId, new ObjectMapper());
    }

    /**
     * Convert a DingTalk webhook payload into a {@link ChannelMessage}. Throws {@link
     * IllegalArgumentException} when the payload is malformed — callers treat that as a 400-class
     * failure (reject the webhook without invoking the handler).
     */
    public ChannelMessage fromDingTalkPayload(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            String msgType = textOr(root, "msgtype", "text");
            String text = root.path("text").path("content").asText("");
            String msgId =
                    textOr(
                            root,
                            "msgId",
                            // Fall back to UUID when DingTalk doesn't send one (older bots).
                            UUID.randomUUID().toString());
            String conversationId = textOr(root, "conversationId", "");
            String senderId = textOr(root, "senderId", "");
            String senderNick = textOr(root, "senderNick", "");
            String sessionWebhook = textOr(root, "sessionWebhook", "");
            long rawTs = root.path("createAt").asLong(0L);
            Instant timestamp = rawTs > 0 ? Instant.ofEpochMilli(rawTs) : Instant.now();

            // Destination is the conversation id if present (so replies route to the chat);
            // otherwise fall back to the sender id for 1:1 flows without a conversation id.
            String destination = !conversationId.isBlank() ? conversationId : senderId;
            if (destination.isBlank()) {
                throw new IllegalArgumentException(
                        "DingTalk payload missing both conversationId and senderId");
            }

            Map<String, String> identityAttrs = new LinkedHashMap<>();
            putIfPresent(identityAttrs, ATTR_CONVERSATION_ID, conversationId);
            putIfPresent(identityAttrs, ATTR_SENDER_ID, senderId);
            putIfPresent(identityAttrs, ATTR_SENDER_NICK, senderNick);
            putIfPresent(identityAttrs, ATTR_SESSION_WEBHOOK, sessionWebhook);

            Map<String, String> messageAttrs = new LinkedHashMap<>();
            messageAttrs.put(ATTR_MSG_ID, msgId);
            messageAttrs.put(ATTR_MSG_TYPE, msgType);

            return new ChannelMessage(
                    msgId,
                    new ChannelIdentity(channelId, destination, identityAttrs),
                    text,
                    timestamp,
                    messageAttrs);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed DingTalk webhook payload", e);
        }
    }

    /**
     * Render a {@link ChannelMessage} as the JSON payload DingTalk's bot API expects (a plain
     * {@code text} message, optionally @-mentioning users). Mentions are read from {@code
     * ChannelMessage#attributes()} under key {@code dingtalk.atMobiles} (comma-separated mobiles)
     * when present.
     */
    public Map<String, Object> toDingTalkPayload(ChannelMessage message, List<String> atMobiles) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("msgtype", "text");
        Map<String, Object> text = new HashMap<>();
        text.put("content", message.content());
        root.put("text", text);
        if (atMobiles != null && !atMobiles.isEmpty()) {
            Map<String, Object> at = new LinkedHashMap<>();
            at.put("atMobiles", atMobiles);
            at.put("isAtAll", false);
            root.put("at", at);
        }
        return root;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? fallback : n.asText(fallback);
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
