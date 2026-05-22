/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.lsp.LspService;
import io.kairo.lsp.registry.DefaultLanguageServerRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultLspServiceTest {

    @Test
    void enabledForFalseOutsideGitWorktree(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("a.py");
        Files.writeString(file, "");
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        LspService svc = DefaultLspService.builder(registry).build();
        assertThat(svc.enabledFor(file)).isFalse();
    }

    @Test
    void enabledForTrueInsideGitWorktree(@TempDir Path tmp) throws Exception {
        Files.createDirectory(tmp.resolve(".git"));
        Path file = tmp.resolve("a.py");
        Files.writeString(file, "");
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        LspService svc = DefaultLspService.builder(registry).build();
        assertThat(svc.enabledFor(file)).isTrue();
    }

    @Test
    void enabledForFalseForUnknownExtension(@TempDir Path tmp) throws Exception {
        Files.createDirectory(tmp.resolve(".git"));
        Path file = tmp.resolve("a.unknownext");
        Files.writeString(file, "");
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        LspService svc = DefaultLspService.builder(registry).build();
        assertThat(svc.enabledFor(file)).isFalse();
    }

    @Test
    void customPredicateOverridesGitGate(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("a.py");
        Files.writeString(file, "");
        var registry = new DefaultLanguageServerRegistry().registerBuiltIns();
        LspService svc = DefaultLspService.builder(registry).enabledPredicate(p -> true).build();
        assertThat(svc.enabledFor(file)).isTrue();
    }
}
