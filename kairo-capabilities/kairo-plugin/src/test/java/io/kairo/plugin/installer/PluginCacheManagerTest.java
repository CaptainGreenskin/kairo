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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginCacheManagerTest {

    @Test
    void slotForCreatesTypeDirIfMissing(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        Path slot = cache.slotFor("github", "anthropics/claude-plugins-official@main");
        assertThat(slot.getParent()).isDirectory();
        assertThat(slot.getParent().getFileName().toString()).isEqualTo("github");
    }

    @Test
    void slotForIsDeterministic(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        Path a = cache.slotFor("github", "owner/repo@main");
        Path b = cache.slotFor("github", "owner/repo@main");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void slotForDiffersAcrossKeys(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        assertThat(cache.slotFor("github", "a@main"))
                .isNotEqualTo(cache.slotFor("github", "b@main"));
    }

    @Test
    void slotForDiffersAcrossTypes(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        assertThat(cache.slotFor("github", "x")).isNotEqualTo(cache.slotFor("npm", "x"));
    }

    @Test
    void isPopulatedReflectsContent(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        Path slot = cache.slotFor("github", "x");
        assertThat(cache.isPopulated("github", "x")).isFalse();
        Files.createDirectories(slot);
        assertThat(cache.isPopulated("github", "x")).isFalse();
        Files.writeString(slot.resolve("plugin.json"), "{}");
        assertThat(cache.isPopulated("github", "x")).isTrue();
    }

    @Test
    void evictRemovesSlotAndReturnsTrue(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        Path slot = cache.slotFor("github", "x");
        Files.createDirectories(slot.resolve("nested"));
        Files.writeString(slot.resolve("a.txt"), "hi");
        Files.writeString(slot.resolve("nested/b.txt"), "hi");
        assertThat(cache.evict("github", "x")).isTrue();
        assertThat(slot).doesNotExist();
        assertThat(cache.evict("github", "x")).isFalse(); // already gone
    }

    @Test
    void clearAllRemovesEverythingAndRecreatesRoot(@TempDir Path tmp) throws Exception {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        Files.createDirectories(cache.slotFor("github", "x"));
        Files.createDirectories(cache.slotFor("npm", "y"));
        cache.clearAll();
        assertThat(cache.cacheRoot()).isEmptyDirectory();
    }

    @Test
    void cacheRootIsAbsolute(@TempDir Path tmp) {
        var cache = new PluginCacheManager(tmp.resolve("cache"));
        assertThat(cache.cacheRoot().isAbsolute()).isTrue();
    }
}
