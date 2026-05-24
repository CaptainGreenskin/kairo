/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SessionSource;
import org.junit.jupiter.api.Test;

class DeliveryTargetTest {

    @Test
    void parsesLocal() {
        var t = DeliveryTarget.parse("local", null);
        assertThat(t.isLocal()).isTrue();
        assertThat(t.toString()).isEqualTo("local");
    }

    @Test
    void parsesLocalForNullSpec() {
        assertThat(DeliveryTarget.parse(null, null).isLocal()).isTrue();
    }

    @Test
    void parsesOriginWithSource() {
        var src = new SessionSource("telegram", "12345", "user-1", "t-9", "group");
        var t = DeliveryTarget.parse("origin", src);
        assertThat(t.isOrigin()).isTrue();
        assertThat(t.channelId()).isEqualTo("telegram");
        assertThat(t.chatId()).isEqualTo("12345");
        assertThat(t.threadId()).isEqualTo("t-9");
    }

    @Test
    void originWithoutSourceFallsBackToLocal() {
        assertThat(DeliveryTarget.parse("origin", null).isLocal()).isTrue();
    }

    @Test
    void parsesChannelOnly() {
        var t = DeliveryTarget.parse("telegram", null);
        assertThat(t.channelId()).isEqualTo("telegram");
        assertThat(t.chatId()).isNull();
        assertThat(t.toString()).isEqualTo("telegram");
    }

    @Test
    void parsesChannelChat() {
        var t = DeliveryTarget.parse("Telegram:CHAT-1", null);
        assertThat(t.channelId()).isEqualTo("telegram");
        assertThat(t.chatId()).isEqualTo("CHAT-1");
        assertThat(t.toString()).isEqualTo("telegram:CHAT-1");
    }

    @Test
    void parsesChannelChatThread() {
        var t = DeliveryTarget.parse("feishu:room:thread-99", null);
        assertThat(t.channelId()).isEqualTo("feishu");
        assertThat(t.chatId()).isEqualTo("room");
        assertThat(t.threadId()).isEqualTo("thread-99");
    }

    @Test
    void rejectsBlankChannelId() {
        assertThatThrownBy(() -> new DeliveryTarget("", null, null, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factoryHelpersWork() {
        assertThat(DeliveryTarget.channel("x").channelId()).isEqualTo("x");
        assertThat(DeliveryTarget.chat("x", "y").chatId()).isEqualTo("y");
        assertThat(DeliveryTarget.local().isLocal()).isTrue();
    }
}
