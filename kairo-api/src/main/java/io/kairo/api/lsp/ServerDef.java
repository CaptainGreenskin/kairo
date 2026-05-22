/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.api.lsp;

import io.kairo.api.Experimental;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declarative description of one language server. Registered via {@link LanguageServerRegistry}.
 *
 * <p>{@code rootMarkers} is the set of file/dir names that anchor the workspace root walking upward
 * from the edited file (e.g. {@code package.json}, {@code go.mod}, {@code Cargo.toml}). Falls back
 * to the git repo root or the file's directory when no marker matches.
 *
 * <p>{@code command} is the literal argv to spawn; resolution / install of the binary itself is
 * delegated to the host (PATH lookup is the default).
 *
 * @since 1.3 (Experimental)
 */
@Experimental("LSP SPI — contract may change in v1.x")
public record ServerDef(
        String serverId,
        String displayName,
        Set<String> supportedExtensions,
        Set<String> rootMarkers,
        List<String> command,
        String languageId) {

    public ServerDef {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(displayName, "displayName");
        supportedExtensions =
                supportedExtensions == null ? Set.of() : Set.copyOf(supportedExtensions);
        rootMarkers = rootMarkers == null ? Set.of() : Set.copyOf(rootMarkers);
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        command = List.copyOf(command);
        Objects.requireNonNull(languageId, "languageId");
    }

    public boolean handles(String filename) {
        if (filename == null) return false;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return false;
        return supportedExtensions.contains(filename.substring(dot + 1).toLowerCase());
    }
}
