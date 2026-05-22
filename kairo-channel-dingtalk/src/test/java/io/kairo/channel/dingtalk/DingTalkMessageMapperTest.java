/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.channel.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.MessageType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DingTalkMessageMapperTest {

    private final DingTalkMessageMapper mapper = new DingTalkMessageMapper("dingtalk");

    @Test
    void fromTextPayloadProducesTextMessage() {
        String raw =
                "{\"msgtype\":\"text\",\"text\":{\"content\":\"hello\"},"
                        + "\"msgId\":\"m1\",\"conversationId\":\"c1\","
                        + "\"senderId\":\"u1\",\"senderNick\":\"Alice\","
                        + "\"createAt\":1700000000000}";
        ChannelMessage msg = mapper.fromDingTalkPayload(raw);
        assertThat(msg.type()).isEqualTo(MessageType.TEXT);
        assertThat(msg.text()).isEqualTo("hello");
        assertThat(msg.messageId()).isEqualTo("m1");
        assertThat(msg.source().channelId()).isEqualTo("dingtalk");
        assertThat(msg.source().chatId()).isEqualTo("c1");
        assertThat(msg.source().userId()).isEqualTo("u1");
        assertThat(msg.source().chatType()).isEqualTo("group");
        assertThat(msg.attributes())
                .containsEntry(DingTalkMessageMapper.ATTR_SENDER_NICK, "Alice")
                .containsEntry(DingTalkMessageMapper.ATTR_MSG_TYPE, "text");
    }

    @Test
    void dmFallsBackToSenderIdAsChatId() {
        String raw =
                "{\"msgtype\":\"text\",\"text\":{\"content\":\"hi\"},"
                        + "\"msgId\":\"m2\",\"senderId\":\"user-only\"}";
        ChannelMessage msg = mapper.fromDingTalkPayload(raw);
        assertThat(msg.source().chatId()).isEqualTo("user-only");
        assertThat(msg.source().chatType()).isEqualTo("dm");
    }

    @Test
    void rejectsMissingDestination() {
        String raw = "{\"msgtype\":\"text\",\"text\":{\"content\":\"\"},\"msgId\":\"m3\"}";
        assertThatThrownBy(() -> mapper.fromDingTalkPayload(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> mapper.fromDingTalkPayload("{not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonTextMsgTypeMapsToOther() {
        String raw =
                "{\"msgtype\":\"richText\",\"text\":{\"content\":\"\"},"
                        + "\"msgId\":\"m4\",\"conversationId\":\"c\",\"senderId\":\"u\"}";
        ChannelMessage msg = mapper.fromDingTalkPayload(raw);
        assertThat(msg.type()).isEqualTo(MessageType.OTHER);
        assertThat(msg.attributes()).containsEntry(DingTalkMessageMapper.ATTR_MSG_TYPE, "richText");
    }

    @Test
    void toPayloadEmitsTextStructure() {
        var payload = mapper.toDingTalkPayload("hello", List.of());
        assertThat(payload).containsEntry("msgtype", "text");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> text = (java.util.Map<String, Object>) payload.get("text");
        assertThat(text).containsEntry("content", "hello");
        assertThat(payload).doesNotContainKey("at");
    }

    @Test
    void toPayloadIncludesAtMobiles() {
        var payload = mapper.toDingTalkPayload("hi", List.of("13800000000"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> at = (java.util.Map<String, Object>) payload.get("at");
        assertThat(at).containsEntry("isAtAll", false);
        @SuppressWarnings("unchecked")
        List<String> mobiles = (List<String>) at.get("atMobiles");
        assertThat(mobiles).containsExactly("13800000000");
    }
}
