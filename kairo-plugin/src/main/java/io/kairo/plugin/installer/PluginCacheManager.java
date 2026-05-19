/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.installer;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Owns the on-disk cache for fetched plugin bytes.
 *
 * <p>Layout:
 *
 * <pre>
 *   &lt;cacheRoot&gt;/
 *     &lt;type&gt;/
 *       &lt;hashed-key&gt;/                   ← one cache slot per source identity
 *         (unpacked plugin tree lives here)
 * </pre>
 *
 * <p>Each remote source variant ({@code GitHub}, {@code GitUrl}, {@code Npm}) hashes its identity
 * string into the same key, so re-fetching the same source is idempotent.
 */
public final class PluginCacheManager {

    private final Path cacheRoot;

    /**
     * @param cacheRoot directory the manager owns; must be writeable. Typically {@code
     *     ~/.kairo/plugins/cache/}.
     */
    public PluginCacheManager(Path cacheRoot) {
        this.cacheRoot = cacheRoot.toAbsolutePath();
    }

    public Path cacheRoot() {
        return cacheRoot;
    }

    /**
     * Returns the slot directory for one source. Creates parents but not the slot itself — fetchers
     * usually want to write to it from scratch.
     */
    public Path slotFor(String type, String identityKey) throws IOException {
        Path typeDir = cacheRoot.resolve(type);
        Files.createDirectories(typeDir);
        return typeDir.resolve(hashKey(identityKey));
    }

    /** Removes a cache slot recursively. Returns true iff something was deleted. */
    public boolean evict(String type, String identityKey) throws IOException {
        Path slot = slotFor(type, identityKey);
        return deleteRecursive(slot);
    }

    /** Removes everything under the cache root. Used by {@code kairo plugin prune}. */
    public void clearAll() throws IOException {
        deleteRecursive(cacheRoot);
        Files.createDirectories(cacheRoot);
    }

    /** True iff the slot exists and is non-empty (the previous fetch finished). */
    public boolean isPopulated(String type, String identityKey) throws IOException {
        Path slot = slotFor(type, identityKey);
        if (!Files.isDirectory(slot)) return false;
        try (var stream = Files.list(slot)) {
            return stream.findAny().isPresent();
        }
    }

    private static boolean deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) return false;
        Files.walkFileTree(
                path,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
        return true;
    }

    private static String hashKey(String identityKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest =
                    md.digest(identityKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 16 hex chars = 8 bytes — short, collision-free in practice for the cache space.
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
