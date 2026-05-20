/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginEnvironmentTest {

    @Test
    void emptyEnvironmentReturnsOriginalPathUnchanged() {
        var env = new PluginEnvironment();
        assertThat(env.augmentedPath("/usr/bin:/bin")).isEqualTo("/usr/bin:/bin");
        assertThat(env.augmentedPath(null)).isEmpty();
        assertThat(env.isEmpty()).isTrue();
    }

    @Test
    void singlePluginBinDirIsPrepended() {
        var env = new PluginEnvironment();
        env.addBinDir("plug-a", Path.of("/opt/plug-a/bin"));
        String result = env.augmentedPath("/usr/bin");
        assertThat(result).startsWith("/opt/plug-a/bin");
        assertThat(result).endsWith("/usr/bin");
        assertThat(result).contains(File.pathSeparator);
    }

    @Test
    void multiplePluginsPreservePluginOrderForDeterminism() {
        var env = new PluginEnvironment();
        env.addBinDir("plug-c", Path.of("/c"));
        env.addBinDir("plug-a", Path.of("/a"));
        env.addBinDir("plug-b", Path.of("/b"));
        // Sorted by plugin id (a, b, c) for stable ordering across runs.
        assertThat(env.activeBinDirs())
                .extracting(Path::toString)
                .containsExactly("/a", "/b", "/c");
    }

    @Test
    void duplicatePathsAreDeduplicated() {
        var env = new PluginEnvironment();
        env.addBinDir("plug-a", Path.of("/dup"));
        env.addBinDir("plug-a", Path.of("/dup"));
        env.addBinDir("plug-a", Path.of("/dup"));
        assertThat(env.activeBinDirs()).hasSize(1);
    }

    @Test
    void removeForPluginDropsAllItsPaths() {
        var env = new PluginEnvironment();
        env.addBinDir("plug-a", Path.of("/a1"));
        env.addBinDir("plug-a", Path.of("/a2"));
        env.addBinDir("plug-b", Path.of("/b1"));
        env.removeForPlugin("plug-a");
        assertThat(env.activeBinDirs()).extracting(Path::toString).containsExactly("/b1");
    }

    @Test
    void removeForPluginIsNoOpForUnknownId() {
        var env = new PluginEnvironment();
        env.addBinDir("plug-a", Path.of("/a"));
        env.removeForPlugin("never-was-here");
        assertThat(env.activeBinDirs()).hasSize(1);
    }

    @Test
    void addBinDirsAcceptsList() {
        var env = new PluginEnvironment();
        env.addBinDirs("plug", List.of(Path.of("/x"), Path.of("/y")));
        assertThat(env.activeBinDirs()).hasSize(2);
    }

    @Test
    void envWithAugmentedPathBuildsFreshMap() {
        var env = new PluginEnvironment();
        env.addBinDir("plug", Path.of("/plug-bin"));
        Map<String, String> result =
                env.envWithAugmentedPath(Map.of("FOO", "bar", "PATH", "/orig"));
        assertThat(result).containsEntry("FOO", "bar");
        assertThat(result.get("PATH")).startsWith("/plug-bin").contains("/orig");
    }

    @Test
    void envWithAugmentedPathFallsBackToHostPath() {
        var env = new PluginEnvironment();
        env.addBinDir("plug", Path.of("/plug-bin"));
        // Without PATH in caller env, falls back to System.getenv("PATH").
        Map<String, String> result = env.envWithAugmentedPath(Map.of());
        assertThat(result).containsKey("PATH");
        assertThat(result.get("PATH")).startsWith("/plug-bin");
    }

    @Test
    void absolutePathsAreStoredEvenWhenInputIsRelative() {
        var env = new PluginEnvironment();
        env.addBinDir("plug", Path.of("relative/bin"));
        assertThat(env.activeBinDirs().get(0).isAbsolute()).isTrue();
    }

    @Test
    void snapshotIsImmutable() {
        var env = new PluginEnvironment();
        env.addBinDir("plug", Path.of("/x"));
        Map<String, List<Path>> snap = env.snapshot();
        // Modifying the env after snapshot should not be reflected in the snapshot.
        env.addBinDir("plug2", Path.of("/y"));
        assertThat(snap.keySet()).containsExactly("plug");
    }

    @Test
    void nullArgumentsAreIgnored() {
        var env = new PluginEnvironment();
        env.addBinDir(null, Path.of("/x"));
        env.addBinDir("plug", null);
        env.addBinDirs("plug", null);
        assertThat(env.isEmpty()).isTrue();
    }
}
