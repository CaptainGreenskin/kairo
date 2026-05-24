/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.testsupport;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Helpers for fixture plugins under {@code src/test/resources/fixtures/}. */
public final class Fixtures {

    private Fixtures() {}

    /**
     * Copies the fixture directory at {@code fixtures/<name>} into a fresh temp dir and returns the
     * temp root. Used because {@link io.kairo.plugin.ClaudePluginLoader} resolves real {@code
     * Path}s and cannot read from a JAR resource directly.
     */
    public static Path copyToTemp(String name) throws IOException {
        URL url = Fixtures.class.getClassLoader().getResource("fixtures/" + name);
        if (url == null) {
            throw new IllegalArgumentException("Fixture not found: " + name);
        }
        Path source;
        try {
            source = Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException("Cannot resolve fixture URL: " + url, e);
        }
        // Sanitise: temp-dir prefix cannot contain path separators (`name` may be
        // "claude-code-samples/commit-commands" for nested fixture trees).
        String sanitised = name.replaceAll("[^A-Za-z0-9_-]", "-");
        Path tempRoot = Files.createTempDirectory("kairo-plugin-fixture-" + sanitised + "-");
        // Land the copy inside a child dir whose name matches the original plugin folder, so
        // PluginLoader's manifest synthesis ({@code pluginRoot.getFileName()}) sees the real
        // plugin name instead of the random temp prefix.
        String leafName = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        Path target = tempRoot.resolve(leafName);
        Files.createDirectory(target);
        copyTree(source, target);
        return target;
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        Files.walkFileTree(
                src,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        Path rel = src.relativize(dir);
                        Path target = dst.resolve(rel.toString());
                        if (!Files.exists(target)) Files.createDirectories(target);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Path rel = src.relativize(file);
                        Files.copy(file, dst.resolve(rel.toString()));
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
