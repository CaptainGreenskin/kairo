/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway.cmd;

import io.kairo.api.gateway.ChannelMessage;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import reactor.core.publisher.Mono;

/**
 * Cross-channel slash-command registry. Keeps the gateway from sprouting platform-specific
 * conditionals (e.g. "if Telegram and starts with /reset, do X") — every adapter ships text up to
 * the gateway and the registry decides whether it's a known command, dispatches to a handler, and
 * returns the reply text.
 *
 * <p>Commands are matched case-insensitively on the bare verb (e.g. {@code /reset} matches {@code
 * /Reset@bot}). {@code @bot} mentions and the leading slash are stripped before lookup. Handlers
 * receive the remaining argument text plus the originating message.
 *
 * <p>Built-in {@code /help} lists every registered verb; everything else is application-defined.
 */
public final class SlashCommandRegistry {

    @FunctionalInterface
    public interface Handler {
        Mono<String> handle(String args, ChannelMessage message);
    }

    public record Command(String verb, String description, Handler handler) {}

    private final ConcurrentHashMap<String, Command> commands = new ConcurrentHashMap<>();

    /**
     * Iteration order preserved for {@code /help} so adapters can register in a meaningful order.
     */
    private final CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();

    public SlashCommandRegistry() {
        register("help", "List available commands", (args, msg) -> Mono.just(renderHelp()));
    }

    public synchronized void register(String verb, String description, Handler handler) {
        String norm = normalise(verb);
        if (norm == null) throw new IllegalArgumentException("verb must not be blank");
        if (commands.put(norm, new Command(norm, description, handler)) == null) {
            order.add(norm);
        }
    }

    public List<Command> commands() {
        return order.stream().map(commands::get).toList();
    }

    /**
     * Attempt to dispatch {@code message} as a slash command. Returns empty when the message isn't
     * a command or the verb isn't registered — callers fall back to normal agent routing.
     */
    public Mono<String> tryDispatch(ChannelMessage message) {
        if (!message.isCommand()) return Mono.empty();
        String text = message.text().trim();
        // Strip the leading slash and an optional @bot suffix on the verb.
        int sp = text.indexOf(' ');
        String head = sp < 0 ? text.substring(1) : text.substring(1, sp);
        String args = sp < 0 ? "" : text.substring(sp + 1).trim();
        int at = head.indexOf('@');
        if (at >= 0) head = head.substring(0, at);
        String norm = normalise(head);
        Command cmd = norm == null ? null : commands.get(norm);
        if (cmd == null) return Mono.empty();
        return cmd.handler().handle(args, message);
    }

    public Optional<Command> get(String verb) {
        return Optional.ofNullable(commands.get(normalise(verb)));
    }

    private static String normalise(String v) {
        if (v == null) return null;
        String s = v.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.startsWith("/")) s = s.substring(1);
        return s.isEmpty() ? null : s;
    }

    private String renderHelp() {
        StringBuilder sb = new StringBuilder("Available commands:\n");
        for (String verb : order) {
            Command c = commands.get(verb);
            sb.append("  /").append(verb);
            if (c.description() != null && !c.description().isEmpty()) {
                sb.append(" — ").append(c.description());
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
