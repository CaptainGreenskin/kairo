/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.channel.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.Experimental;
import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.MessageType;
import io.kairo.api.gateway.SessionSource;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps between DingTalk custom-bot webhook JSON and {@link ChannelMessage}. Text content lands in
 * {@link ChannelMessage#text()}; routing metadata (conversation id, sender id, session webhook) is
 * preserved both inside {@link SessionSource} (the typed fields) and {@link
 * ChannelMessage#attributes()} (for round-trip).
 *
 * <p>Only DingTalk's {@code text} message type lands as {@link MessageType#TEXT}; other types (e.g.
 * {@code richText}) are mapped to {@link MessageType#OTHER} with the raw type preserved under
 * attribute {@link #ATTR_MSG_TYPE}.
 *
 * @since v1.2 (Experimental, post-gateway-collapse)
 */
@Experimental("DingTalk message mapper — contract may change in v1.x")
public final class DingTalkMessageMapper {

    /** Attribute key for the DingTalk message id (used for idempotency). */
    public static final String ATTR_MSG_ID = "dingtalk.msgId";

    /** Attribute key for the raw DingTalk message type, e.g. {@code "text"}. */
    public static final String ATTR_MSG_TYPE = "dingtalk.msgType";

    /** Attribute key for the DingTalk conversation id (group chat or 1:1). */
    public static final String ATTR_CONVERSATION_ID = "dingtalk.conversationId";

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
            String rawType = textOr(root, "msgtype", "text");
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

            // chatId is the conversation when present (so replies route to the group);
            // otherwise senderId for 1:1 flows without a conversation id.
            String chatId = !conversationId.isBlank() ? conversationId : senderId;
            if (chatId.isBlank()) {
                throw new IllegalArgumentException(
                        "DingTalk payload missing both conversationId and senderId");
            }
            String chatType = !conversationId.isBlank() ? "group" : "dm";

            SessionSource source =
                    new SessionSource(
                            channelId,
                            chatId,
                            senderId.isBlank() ? null : senderId,
                            null,
                            chatType);

            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put(ATTR_MSG_ID, msgId);
            attributes.put(ATTR_MSG_TYPE, rawType);
            putIfPresent(attributes, ATTR_CONVERSATION_ID, conversationId);
            putIfPresent(attributes, ATTR_SENDER_NICK, senderNick);
            putIfPresent(attributes, ATTR_SESSION_WEBHOOK, sessionWebhook);

            MessageType type = "text".equals(rawType) ? MessageType.TEXT : MessageType.OTHER;

            return new ChannelMessage(
                    msgId, source, type, text, List.of(), msgId, null, null, timestamp, attributes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed DingTalk webhook payload", e);
        }
    }

    /**
     * Render outbound text as the JSON payload DingTalk's bot API expects (a plain {@code text}
     * message, optionally @-mentioning users). Adapters wire this from {@code Channel#send(target,
     * content, replyToMessageId, metadata)}; mentions come from {@code metadata.get("atMobiles")}
     * as a {@code List<String>}, or default to {@code atMobiles} configured on the channel.
     */
    public Map<String, Object> toDingTalkPayload(String content, List<String> atMobiles) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("msgtype", "text");
        Map<String, Object> text = new HashMap<>();
        text.put("content", content == null ? "" : content);
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

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
