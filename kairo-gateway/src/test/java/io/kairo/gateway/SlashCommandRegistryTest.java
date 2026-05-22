/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.SessionSource;
import io.kairo.gateway.cmd.SlashCommandRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SlashCommandRegistryTest {

    @Test
    void helpIsRegisteredByDefault() {
        var r = new SlashCommandRegistry();
        var help =
                r.tryDispatch(ChannelMessage.text(SessionSource.of("x", "y", "z"), "/help"))
                        .block();
        assertThat(help).contains("/help");
    }

    @Test
    void registerCustomCommand() {
        var r = new SlashCommandRegistry();
        r.register("reset", "Reset agent state", (args, msg) -> Mono.just("ok-" + args));
        var out =
                r.tryDispatch(ChannelMessage.text(SessionSource.of("x", "y", "z"), "/reset hard"))
                        .block();
        assertThat(out).isEqualTo("ok-hard");
    }

    @Test
    void caseInsensitiveAndAtBotStripped() {
        var r = new SlashCommandRegistry();
        r.register("ping", "ping", (args, msg) -> Mono.just("pong"));
        var out =
                r.tryDispatch(
                                ChannelMessage.text(
                                        SessionSource.of("x", "y", "z"), "/Ping@kairo-bot"))
                        .block();
        assertThat(out).isEqualTo("pong");
    }

    @Test
    void unknownVerbReturnsEmpty() {
        var r = new SlashCommandRegistry();
        var out = r.tryDispatch(ChannelMessage.text(SessionSource.of("x", "y", "z"), "/unknown"));
        assertThat(out.block()).isNull();
    }

    @Test
    void nonCommandReturnsEmpty() {
        var r = new SlashCommandRegistry();
        var out = r.tryDispatch(ChannelMessage.text(SessionSource.of("x", "y", "z"), "hello"));
        assertThat(out.block()).isNull();
    }
}
