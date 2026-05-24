/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.gateway.mirror;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.SendResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSONL mirror — one line per event, opened in APPEND mode. Cheap to write, trivial to grep, easy
 * to replay later (e.g. via {@code jq} or by feeding events back into a test harness).
 *
 * <p>Each line is a small map: {@code {"direction":"in|out", "timestamp":..., "channelId":..., …}}.
 * Bytes are UTF-8; failures are logged at warn (so a broken disk doesn't take the gateway down) but
 * never thrown.
 */
public final class JsonlMirrorStore implements MirrorStore {

    private static final Logger log = LoggerFactory.getLogger(JsonlMirrorStore.class);

    private final Path file;
    private final ObjectMapper mapper;

    public JsonlMirrorStore(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
        } catch (IOException e) {
            log.warn("Failed to ensure mirror directory {}: {}", file.getParent(), e.getMessage());
        }
    }

    @Override
    public void recordInbound(ChannelMessage message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("direction", "in");
        row.put("timestamp", Instant.now().toString());
        row.put("channelId", message.source().channelId());
        row.put("chatId", message.source().chatId());
        row.put("userId", message.source().userId());
        row.put("threadId", message.source().threadId());
        row.put("messageId", message.messageId());
        row.put("traceId", message.id());
        row.put("type", message.type().name());
        row.put("text", message.text());
        row.put("attachments", message.attachments().size());
        write(row);
    }

    @Override
    public void recordOutbound(DeliveryTarget target, String content, SendResult result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("direction", "out");
        row.put("timestamp", Instant.now().toString());
        row.put("channelId", target.channelId());
        row.put("chatId", target.chatId());
        row.put("threadId", target.threadId());
        row.put("text", content);
        row.put("ok", result.success());
        row.put("messageId", result.messageId());
        if (!result.success()) {
            row.put("error", result.errorMessage());
            row.put(
                    "failureMode",
                    result.failureMode() == null ? null : result.failureMode().name());
        }
        write(row);
    }

    @Override
    public void recordOutboundLocal(String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("direction", "out");
        row.put("timestamp", Instant.now().toString());
        row.put("channelId", DeliveryTarget.LOCAL_CHANNEL);
        row.put("text", content);
        row.put("ok", true);
        write(row);
    }

    private void write(Map<String, Object> row) {
        try {
            String line = mapper.writeValueAsString(row) + "\n";
            Files.writeString(
                    file,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to write mirror row to {}: {}", file, e.getMessage());
        }
    }
}
