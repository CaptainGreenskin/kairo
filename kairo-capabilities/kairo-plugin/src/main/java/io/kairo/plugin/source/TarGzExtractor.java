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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Decompresses a {@code .tar.gz} stream into a directory.
 *
 * <p>Used by the {@link GitHubSourceFetcher} (GitHub repository archives are tarballs) and the
 * {@link NpmSourceFetcher} (npm packages are tarballs). Strips the conventional single-directory
 * prefix that both producers use, returning the content directory directly.
 *
 * <p>Hardened against:
 *
 * <ul>
 *   <li>Path traversal — entries that escape {@code targetDir} are rejected
 *   <li>Permission preservation — POSIX execute bits on regular files are kept where the FS
 *       supports them (so plugin {@code bin/} scripts stay runnable)
 * </ul>
 */
public final class TarGzExtractor {

    private TarGzExtractor() {}

    /**
     * Extracts {@code source} into {@code targetDir}. The stream is consumed; the caller manages
     * its lifecycle.
     *
     * @param stripComponents number of leading path components to drop from each entry name (e.g. 1
     *     for GitHub tarballs that wrap content in a {@code {repo}-{ref}/} directory)
     * @return the deepest directory that contains real content (i.e. {@code targetDir} for
     *     stripComponents=0, or {@code targetDir} after the prefix has been stripped — both end up
     *     equal in practice)
     */
    public static Path extract(InputStream source, Path targetDir, int stripComponents)
            throws IOException {
        Files.createDirectories(targetDir);
        try (var bin = new BufferedInputStream(source);
                var gz = new GZIPInputStream(bin);
                var tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                String name = stripPrefix(entry.getName(), stripComponents);
                if (name == null || name.isEmpty()) continue;
                Path out = targetDir.resolve(name).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("Tar entry escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    Files.copy(tar, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    applyMode(out, entry.getMode());
                }
            }
        }
        return targetDir;
    }

    private static String stripPrefix(String name, int components) {
        if (components <= 0) return name;
        int idx = -1;
        for (int i = 0; i < components; i++) {
            idx = name.indexOf('/', idx + 1);
            if (idx < 0) return null;
        }
        return idx + 1 < name.length() ? name.substring(idx + 1) : null;
    }

    private static void applyMode(Path file, int mode) {
        if (mode == 0) return;
        try {
            if ((mode & 0111) != 0) {
                Set<PosixFilePermission> perms;
                try {
                    perms = Files.getPosixFilePermissions(file);
                } catch (UnsupportedOperationException e) {
                    return; // FS doesn't support POSIX (e.g. Windows)
                }
                perms = new java.util.HashSet<>(perms);
                if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);
                if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);
                if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(file, perms);
            }
        } catch (IOException ignored) {
            // best-effort; permission application failure shouldn't abort extraction
        }
    }

    /** For diagnostics: returns a default permission set for an executable file. */
    public static Set<PosixFilePermission> executablePerms() {
        return PosixFilePermissions.fromString("rwxr-xr-x");
    }
}
