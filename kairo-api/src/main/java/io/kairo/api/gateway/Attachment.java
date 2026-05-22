/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.gateway;

import io.kairo.api.Experimental;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A single media attachment carried by an inbound or outbound rich gateway message. Pure value type
 * — adapters are responsible for materialising bytes (caching them in {@link #localPath}) before
 * handing the message to the gateway, so downstream consumers (vision tools, transcription tools)
 * can read directly without re-fetching from the platform.
 *
 * @param type one of {@link MessageType#IMAGE}, {@link MessageType#VIDEO}, etc.
 * @param localPath on-disk path the gateway can read; null for outbound when the adapter handles
 *     upload itself from a URL
 * @param remoteUrl original URL on the platform — useful for re-render or fallback delivery, null
 *     when the file only exists locally
 * @param mimeType IANA media type when known ({@code image/png}, {@code audio/ogg}), else null
 * @param fileName display name suggested for the file ({@code voice-20260522.ogg}); adapters use
 *     this when uploading
 * @param sizeBytes file size when known, -1 otherwise
 * @since 1.2 (Experimental)
 */
@Experimental("Gateway SPI — contract may change in v1.x")
public record Attachment(
        MessageType type,
        Path localPath,
        String remoteUrl,
        String mimeType,
        String fileName,
        long sizeBytes) {

    public Attachment {
        Objects.requireNonNull(type, "type");
        if (localPath == null && (remoteUrl == null || remoteUrl.isBlank())) {
            throw new IllegalArgumentException("Attachment requires either localPath or remoteUrl");
        }
    }

    /** Local-file attachment with all metadata derivable from the path. */
    public static Attachment ofLocal(MessageType type, Path path, String mimeType) {
        Objects.requireNonNull(path, "path");
        long size = -1L;
        try {
            size = java.nio.file.Files.size(path);
        } catch (Exception ignored) {
            // size lookup is best-effort; nothing in the contract depends on it.
        }
        return new Attachment(type, path, null, mimeType, path.getFileName().toString(), size);
    }

    /** Remote-URL attachment that the platform will fetch when sending. */
    public static Attachment ofRemote(MessageType type, String url, String mimeType) {
        return new Attachment(type, null, url, mimeType, null, -1L);
    }
}
