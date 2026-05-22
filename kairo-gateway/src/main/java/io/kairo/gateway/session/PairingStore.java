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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent map from a {@code (channelId, userId)} pair on one platform to a logical Kairo user
 * id. Lets the agent recognise that the human chatting from Telegram is the same one who chats from
 * WeCom, so per-user policies (allowlist, rate limit, memory) apply consistently across channels.
 *
 * <p>Storage is a single JSON file on disk; the in-memory copy is the source of truth, the file is
 * rewritten atomically on every mutation. Acceptable up to thousands of pairings; if a deployment
 * outgrows that, swap in a Redis-backed implementation that conforms to the same read/write
 * semantics.
 */
public final class PairingStore {

    private static final Logger log = LoggerFactory.getLogger(PairingStore.class);

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    public PairingStore(Path file) {
        this.file = file;
        load();
    }

    /** In-memory only — useful for tests. */
    public PairingStore() {
        this(null);
    }

    /**
     * Pair {@code (channelId, userId)} with the given Kairo user id. Overwrites any prior mapping
     * for the same pair.
     */
    public synchronized void pair(String channelId, String userId, String kairoUserId) {
        map.put(key(channelId, userId), kairoUserId);
        persist();
    }

    public Optional<String> lookup(String channelId, String userId) {
        return Optional.ofNullable(map.get(key(channelId, userId)));
    }

    public synchronized boolean unpair(String channelId, String userId) {
        boolean removed = map.remove(key(channelId, userId)) != null;
        if (removed) persist();
        return removed;
    }

    public int size() {
        return map.size();
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(map);
    }

    private static String key(String channelId, String userId) {
        return channelId + "|" + userId;
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> read = mapper.readValue(file.toFile(), Map.class);
            map.putAll(read);
        } catch (IOException e) {
            log.warn("Failed to load pairings from {}: {}", file, e.getMessage());
        }
    }

    private void persist() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            // Write to a sibling temp file then atomic-rename to avoid partial writes corrupting
            // the pairings on power loss.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(tmp.toFile(), new LinkedHashMap<>(map));
            Files.move(
                    tmp,
                    file,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("Failed to persist pairings to {}: {}", file, e.getMessage());
        }
    }
}
