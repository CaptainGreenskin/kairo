/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway.session;

import io.kairo.api.gateway.SessionSource;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory map from {@link SessionSource} → stable session id. Lets agent runners answer "is this
 * message a continuation of a session I already have a working agent for?" without persisting
 * anything outside Kairo's own session store.
 *
 * <p>Key derivation folds {@code (channelId, chatId, threadId)} into a stable string; {@code
 * userId} is intentionally excluded so a group chat with multiple participants resolves to a single
 * shared session (the agent handles per-user differentiation at a higher layer). Threads get their
 * own session so a Telegram forum topic doesn't pollute the main channel's history.
 *
 * <p>The directory grows on demand — first call to {@link #idFor(SessionSource)} mints a UUID for a
 * new source and remembers it. {@link #clear(String)} lets callers reset a logical session (e.g.
 * after a {@code /reset} slash command).
 */
public final class SessionDirectory {

    /**
     * Stored payload — kept tiny on purpose since the directory may live for the lifetime of the
     * JVM.
     */
    public record Entry(String sessionId, Instant firstSeen, Instant lastSeen, long messageCount) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /** Note a sighting; returns the (possibly newly-minted) session id. */
    public String note(SessionSource source) {
        String key = key(source);
        Entry updated =
                entries.compute(
                        key,
                        (k, existing) -> {
                            Instant now = Instant.now();
                            if (existing == null) {
                                return new Entry(UUID.randomUUID().toString(), now, now, 1L);
                            }
                            return new Entry(
                                    existing.sessionId(),
                                    existing.firstSeen(),
                                    now,
                                    existing.messageCount() + 1);
                        });
        return updated.sessionId();
    }

    /** Look up the session id without bumping counters. Returns empty if unseen. */
    public Optional<String> idFor(SessionSource source) {
        Entry e = entries.get(key(source));
        return e == null ? Optional.empty() : Optional.of(e.sessionId());
    }

    /** Snapshot of all entries — copy, not live view. */
    public Collection<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public int size() {
        return entries.size();
    }

    /** Drop every entry whose session id matches. Returns true if anything was removed. */
    public boolean clear(String sessionId) {
        boolean[] removed = new boolean[] {false};
        entries.values()
                .removeIf(
                        e -> {
                            if (e.sessionId().equals(sessionId)) {
                                removed[0] = true;
                                return true;
                            }
                            return false;
                        });
        return removed[0];
    }

    /** Drop everything — used by tests and {@code /reset --all} flows. */
    public void clearAll() {
        entries.clear();
    }

    private static String key(SessionSource s) {
        return s.channelId() + "|" + s.chatId() + "|" + (s.threadId() == null ? "" : s.threadId());
    }
}
