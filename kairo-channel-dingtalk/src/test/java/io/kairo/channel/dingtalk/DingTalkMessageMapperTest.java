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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.channel.ChannelIdentity;
import io.kairo.api.channel.ChannelMessage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DingTalkMessageMapperTest {

    private final DingTalkMessageMapper mapper = new DingTalkMessageMapper("dingtalk-test");

    @Test
    void fromPayload_preservesRoutingMetadata() {
        String raw =
                """
                {
                  "msgtype": "text",
                  "msgId": "msg-abc",
                  "createAt": 1700000000000,
                  "conversationId": "conv-123",
                  "senderId": "sender-xyz",
                  "senderNick": "Alice",
                  "sessionWebhook": "https://oapi.dingtalk.com/robot/sendBySession?session=abc",
                  "text": { "content": "summarize recent PRs" }
                }
                """;

        ChannelMessage msg = mapper.fromDingTalkPayload(raw);

        assertThat(msg.id()).isEqualTo("msg-abc");
        assertThat(msg.content()).isEqualTo("summarize recent PRs");
        assertThat(msg.timestamp()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
        assertThat(msg.identity().channelId()).isEqualTo("dingtalk-test");
        assertThat(msg.identity().destination()).isEqualTo("conv-123");
        assertThat(msg.identity().attributes())
                .containsEntry(DingTalkMessageMapper.ATTR_CONVERSATION_ID, "conv-123")
                .containsEntry(DingTalkMessageMapper.ATTR_SENDER_ID, "sender-xyz")
                .containsEntry(DingTalkMessageMapper.ATTR_SENDER_NICK, "Alice")
                .containsEntry(
                        DingTalkMessageMapper.ATTR_SESSION_WEBHOOK,
                        "https://oapi.dingtalk.com/robot/sendBySession?session=abc");
        assertThat(msg.attributes())
                .containsEntry(DingTalkMessageMapper.ATTR_MSG_ID, "msg-abc")
                .containsEntry(DingTalkMessageMapper.ATTR_MSG_TYPE, "text");
    }

    @Test
    void fromPayload_fallsBackToSenderIdAsDestination_whenConversationIdMissing() {
        String raw =
                """
                {
                  "msgtype": "text",
                  "msgId": "msg-1",
                  "senderId": "sender-only",
                  "text": { "content": "hi" }
                }
                """;

        ChannelMessage msg = mapper.fromDingTalkPayload(raw);

        assertThat(msg.identity().destination()).isEqualTo("sender-only");
    }

    @Test
    void fromPayload_rejectsWhenBothDestinationsMissing() {
        String raw =
                """
                {
                  "msgtype": "text",
                  "msgId": "msg-1",
                  "text": { "content": "hi" }
                }
                """;

        assertThatThrownBy(() -> mapper.fromDingTalkPayload(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationId");
    }

    @Test
    void fromPayload_rejectsMalformedJson() {
        assertThatThrownBy(() -> mapper.fromDingTalkPayload("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toPayload_rendersTextWithOptionalAtMobiles() {
        ChannelMessage msg =
                ChannelMessage.of(
                        ChannelIdentity.of("dingtalk-test", "conv-42"), "the PR is green");

        Map<String, Object> noAt = mapper.toDingTalkPayload(msg, List.of());
        assertThat(noAt).containsEntry("msgtype", "text");
        assertThat(noAt).doesNotContainKey("at");
        @SuppressWarnings("unchecked")
        Map<String, Object> text = (Map<String, Object>) noAt.get("text");
        assertThat(text).containsEntry("content", "the PR is green");

        Map<String, Object> withAt = mapper.toDingTalkPayload(msg, List.of("13800138000"));
        @SuppressWarnings("unchecked")
        Map<String, Object> at = (Map<String, Object>) withAt.get("at");
        assertThat(at).containsEntry("isAtAll", false);
        assertThat((List<?>) at.get("atMobiles")).singleElement().isEqualTo("13800138000");
    }
}
