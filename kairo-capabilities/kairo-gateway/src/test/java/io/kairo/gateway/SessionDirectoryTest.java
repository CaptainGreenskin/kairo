/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.SessionSource;
import io.kairo.gateway.session.SessionDirectory;
import org.junit.jupiter.api.Test;

class SessionDirectoryTest {

    @Test
    void noteMintsNewSessionIdOnce() {
        var dir = new SessionDirectory();
        var src = SessionSource.of("telegram", "chat-1", "user-1");
        String id1 = dir.note(src);
        String id2 = dir.note(src);
        assertThat(id1).isNotBlank().isEqualTo(id2);
        assertThat(dir.size()).isEqualTo(1);
    }

    @Test
    void differentChatGetsDifferentSession() {
        var dir = new SessionDirectory();
        String a = dir.note(SessionSource.of("telegram", "chat-1", "u"));
        String b = dir.note(SessionSource.of("telegram", "chat-2", "u"));
        assertThat(a).isNotEqualTo(b);
        assertThat(dir.size()).isEqualTo(2);
    }

    @Test
    void differentChannelGetsDifferentSession() {
        var dir = new SessionDirectory();
        String a = dir.note(SessionSource.of("telegram", "chat-1", "u"));
        String b = dir.note(SessionSource.of("feishu", "chat-1", "u"));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void threadGetsItsOwnSession() {
        var dir = new SessionDirectory();
        String mainline = dir.note(new SessionSource("telegram", "g-1", "u", null, "group"));
        String topic = dir.note(new SessionSource("telegram", "g-1", "u", "topic-7", "group"));
        assertThat(mainline).isNotEqualTo(topic);
    }

    @Test
    void differentUserInSameChatSharesSession() {
        var dir = new SessionDirectory();
        String u1 = dir.note(SessionSource.of("slack", "C-1", "user-A"));
        String u2 = dir.note(SessionSource.of("slack", "C-1", "user-B"));
        assertThat(u1).isEqualTo(u2);
    }

    @Test
    void clearRemovesEntries() {
        var dir = new SessionDirectory();
        String id = dir.note(SessionSource.of("x", "y", "z"));
        assertThat(dir.clear(id)).isTrue();
        assertThat(dir.size()).isZero();
        assertThat(dir.clear(id)).isFalse();
    }

    @Test
    void idForReturnsEmptyForUnknown() {
        var dir = new SessionDirectory();
        assertThat(dir.idFor(SessionSource.of("x", "y", "z"))).isEmpty();
    }
}
