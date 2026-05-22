/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.registry;

import io.kairo.api.lsp.LanguageServerRegistry;
import io.kairo.api.lsp.ServerDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory registry. Insertion order is preserved (the first-registered server that handles a
 * given extension wins on {@link #findFor(Path)}).
 */
public final class DefaultLanguageServerRegistry implements LanguageServerRegistry {

    private final Map<String, ServerDef> servers = new LinkedHashMap<>();

    @Override
    public synchronized void register(ServerDef def) {
        servers.put(def.serverId(), def);
    }

    @Override
    public synchronized List<ServerDef> all() {
        return List.copyOf(servers.values());
    }

    @Override
    public synchronized Optional<ServerDef> findById(String serverId) {
        return Optional.ofNullable(servers.get(serverId));
    }

    @Override
    public synchronized Optional<ServerDef> findFor(Path filePath) {
        if (filePath == null) return Optional.empty();
        String name = filePath.getFileName() == null ? "" : filePath.getFileName().toString();
        String ext = extension(name);
        if (ext == null) return Optional.empty();
        for (ServerDef def : servers.values()) {
            if (def.supportedExtensions().contains(ext)) return Optional.of(def);
        }
        return Optional.empty();
    }

    @Override
    public Path resolveWorkspaceRoot(Path filePath, ServerDef def) {
        Path abs = filePath.toAbsolutePath().normalize();
        Path start = Files.isDirectory(abs) ? abs : abs.getParent();
        if (start == null) return abs;
        if (def.rootMarkers().isEmpty()) {
            return fallbackRoot(start);
        }
        Path cursor = start;
        while (cursor != null) {
            for (String marker : def.rootMarkers()) {
                if (Files.exists(cursor.resolve(marker))) return cursor;
            }
            cursor = cursor.getParent();
        }
        return fallbackRoot(start);
    }

    /** Walk up looking for a {@code .git} entry, else the original directory. */
    private static Path fallbackRoot(Path start) {
        Path cursor = start;
        while (cursor != null) {
            if (Files.exists(cursor.resolve(".git"))) return cursor;
            cursor = cursor.getParent();
        }
        return start;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Convenience: register every {@link BuiltInServers} entry. */
    public DefaultLanguageServerRegistry registerBuiltIns() {
        for (ServerDef def : BuiltInServers.all()) register(def);
        return this;
    }

    /** Visible for tests: bulk read of insertion order. */
    public List<String> registeredIds() {
        return new ArrayList<>(servers.keySet());
    }
}
