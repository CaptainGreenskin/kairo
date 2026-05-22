/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp.registry;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.lsp.ServerDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultLanguageServerRegistryTest {

    @Test
    void registeredBuiltInsAreFiveAndOrdered() {
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        assertThat(registry.registeredIds())
                .containsExactly(
                        "pyright",
                        "typescript-language-server",
                        "gopls",
                        "rust-analyzer",
                        "clangd");
    }

    @Test
    void findForRoutesByExtension() {
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        assertThat(registry.findFor(Path.of("/x/main.go")))
                .map(ServerDef::serverId)
                .hasValue("gopls");
        assertThat(registry.findFor(Path.of("/x/lib.rs")))
                .map(ServerDef::serverId)
                .hasValue("rust-analyzer");
    }

    @Test
    void unknownExtensionReturnsEmpty() {
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        assertThat(registry.findFor(Path.of("/x/file.unknownext"))).isEmpty();
    }

    @Test
    void resolveWorkspaceRootFindsMarker(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("project/sub"));
        Files.writeString(tmp.resolve("project/go.mod"), "module x");
        Path file = tmp.resolve("project/sub/main.go");
        Files.writeString(file, "package main");
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        var def = registry.findFor(file).orElseThrow();
        Path root = registry.resolveWorkspaceRoot(file, def);
        assertThat(root).isEqualTo(tmp.resolve("project"));
    }

    @Test
    void resolveWorkspaceRootFallsBackToGit(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("repo/sub"));
        Files.createDirectory(tmp.resolve("repo/.git"));
        Path file = tmp.resolve("repo/sub/main.go");
        Files.writeString(file, "");
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        // Use a def with no markers to force fallback
        var bareDef = new ServerDef("bare", "Bare", Set.of("go"), Set.of(), List.of("noop"), "go");
        Path root = registry.resolveWorkspaceRoot(file, bareDef);
        assertThat(root).isEqualTo(tmp.resolve("repo"));
    }

    @Test
    void lastRegistrationWinsOnIdCollision() {
        var registry = new DefaultLanguageServerRegistry();
        registry.register(BuiltInServers.PYRIGHT);
        var replacement =
                new ServerDef(
                        "pyright",
                        "Pyright (replaced)",
                        Set.of("py"),
                        Set.of(),
                        List.of("custom"),
                        "python");
        registry.register(replacement);
        assertThat(registry.findById("pyright").orElseThrow().displayName())
                .isEqualTo("Pyright (replaced)");
    }
}
