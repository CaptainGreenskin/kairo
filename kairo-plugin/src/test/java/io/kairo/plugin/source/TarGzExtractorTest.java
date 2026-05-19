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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TarGzExtractorTest {

    @Test
    void extractsFlatTarballAtStripZero(@TempDir Path tmp) throws Exception {
        byte[] tarball =
                buildTarGz(entry("a.txt", "hello", 0644), entry("nested/b.txt", "world", 0644));
        TarGzExtractor.extract(new ByteArrayInputStream(tarball), tmp, 0);
        assertThat(tmp.resolve("a.txt")).hasContent("hello");
        assertThat(tmp.resolve("nested/b.txt")).hasContent("world");
    }

    @Test
    void stripComponentsRemovesGithubArchivePrefix(@TempDir Path tmp) throws Exception {
        byte[] tarball =
                buildTarGz(
                        entry("repo-main/", null, 0755),
                        entry("repo-main/.kairo-plugin/plugin.json", "{}", 0644),
                        entry("repo-main/SKILL.md", "body", 0644));
        TarGzExtractor.extract(new ByteArrayInputStream(tarball), tmp, 1);
        assertThat(tmp.resolve(".kairo-plugin/plugin.json")).exists();
        assertThat(tmp.resolve("SKILL.md")).hasContent("body");
        // The wrapper directory should NOT be created in the target.
        assertThat(tmp.resolve("repo-main")).doesNotExist();
    }

    @Test
    void rejectsTraversalEntries(@TempDir Path tmp) throws Exception {
        byte[] tarball = buildTarGz(entry("../escape.txt", "evil", 0644));
        assertThatThrownBy(() -> TarGzExtractor.extract(new ByteArrayInputStream(tarball), tmp, 0))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("escapes target");
    }

    @Test
    void preservesExecutableBitWhenSupported(@TempDir Path tmp) throws Exception {
        byte[] tarball = buildTarGz(entry("script.sh", "#!/bin/sh\necho hi\n", 0755));
        TarGzExtractor.extract(new ByteArrayInputStream(tarball), tmp, 0);
        Path out = tmp.resolve("script.sh");
        assertThat(out).exists();
        try {
            var perms = Files.getPosixFilePermissions(out);
            assertThat(perms).contains(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
        } catch (UnsupportedOperationException e) {
            // Windows / non-POSIX FS — skip the assertion silently.
        }
    }

    @Test
    void handlesNestedDirectoryEntriesInAnyOrder(@TempDir Path tmp) throws Exception {
        byte[] tarball =
                buildTarGz(
                        entry("a/b/c.txt", "deep", 0644),
                        entry("a/", null, 0755),
                        entry("a/b/", null, 0755));
        TarGzExtractor.extract(new ByteArrayInputStream(tarball), tmp, 0);
        assertThat(tmp.resolve("a/b/c.txt")).hasContent("deep");
    }

    @Test
    void emptyTarballIsNoOp(@TempDir Path tmp) throws Exception {
        byte[] tarball = buildTarGz(); // no entries
        TarGzExtractor.extract(new ByteArrayInputStream(tarball), tmp, 0);
        assertThat(tmp).isEmptyDirectory();
    }

    @Test
    void stripComponentsBeyondAvailableSilentlyDropsEntry(@TempDir Path tmp) throws Exception {
        byte[] tarball = buildTarGz(entry("only-one-level.txt", "x", 0644));
        TarGzExtractor.extract(new ByteArrayInputStream(tarball), tmp, 1);
        assertThat(tmp).isEmptyDirectory();
    }

    // ── tarball builder ─────────────────────────────────────────────────────

    private static TarEntry entry(String name, String content, int mode) {
        return new TarEntry(name, content, mode);
    }

    private static byte[] buildTarGz(TarEntry... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(baos);
                var tar = new TarArchiveOutputStream(gz)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (TarEntry e : entries) {
                TarArchiveEntry tae = new TarArchiveEntry(e.name);
                tae.setMode(e.mode);
                if (e.content != null) {
                    byte[] bytes = e.content.getBytes(StandardCharsets.UTF_8);
                    tae.setSize(bytes.length);
                    tar.putArchiveEntry(tae);
                    tar.write(bytes);
                } else {
                    tar.putArchiveEntry(tae);
                }
                tar.closeArchiveEntry();
            }
        }
        return baos.toByteArray();
    }

    private record TarEntry(String name, String content, int mode) {}
}
