/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.plugin.PluginSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPathSourceFetcherTest {

    private final LocalPathSourceFetcher fetcher = new LocalPathSourceFetcher();

    @Test
    void supportsLocalPathOnly() {
        assertThat(fetcher.supports(new PluginSource.LocalPath(Path.of("/tmp")))).isTrue();
        assertThat(fetcher.supports(new PluginSource.GitHub("a/b", null, null))).isFalse();
        assertThat(fetcher.supports(new PluginSource.Npm("p", "1.0.0", null))).isFalse();
    }

    @Test
    void kindIsPath() {
        assertThat(fetcher.kind()).isEqualTo("path");
    }

    @Test
    void fetchReturnsAbsolutePathForExistingDir(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("plugin");
        Files.createDirectory(dir);
        Path resolved = fetcher.fetch(new PluginSource.LocalPath(dir)).block(Duration.ofSeconds(2));
        assertThat(resolved).isAbsolute();
        assertThat(resolved).isEqualTo(dir.toAbsolutePath());
    }

    @Test
    void fetchRejectsNonExistentPath(@TempDir Path tmp) {
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.LocalPath(tmp.resolve("missing")))
                                        .block(Duration.ofSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not point at a directory");
    }

    @Test
    void fetchRejectsRegularFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("foo.txt");
        Files.writeString(file, "hi");
        assertThatThrownBy(
                        () ->
                                fetcher.fetch(new PluginSource.LocalPath(file))
                                        .block(Duration.ofSeconds(2)))
                .hasMessageContaining("not point at a directory");
    }
}
