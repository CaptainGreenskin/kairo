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

import io.kairo.api.plugin.PluginComponent;
import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginManifest;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.installer.PluginCacheManager;
import io.kairo.plugin.source.GitSubdirSourceFetcher;
import io.kairo.plugin.source.SourceFetcherRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone runner that downloads several real Claude Code plugins from {@code
 * anthropics/claude-code} and prints what Kairo's loader sees in each. Intended for live human
 * verification during PR review or release sign-off.
 *
 * <p>Run with: {@code mvn -pl kairo-plugin test-compile && java -cp $(mvn -pl kairo-plugin
 * dependency:build-classpath -q -DincludeScope=test -DoutputProperty=cp -Dmdep.outputProperty=cp
 * 2>/dev/null | tail -1):kairo-plugin/target/test-classes:kairo-plugin/target/classes
 * io.kairo.plugin.DogfoodMain}.
 *
 * <p>The first run hits the network (full clone of the {@code claude-code} monorepo for each subdir
 * spec); subsequent runs hit the cache under {@code &lt;tmp&gt;/cache/git-subdir/}.
 */
public final class DogfoodMain {

    private static final String REPO = "https://github.com/anthropics/claude-code.git";
    private static final List<String> SUBDIRS =
            List.of(
                    "plugins/commit-commands",
                    "plugins/explanatory-output-style",
                    "plugins/frontend-design",
                    "plugins/pr-review-toolkit",
                    "plugins/hookify");

    private DogfoodMain() {}

    public static void main(String[] args) throws Exception {
        // Stable cache root so re-runs are quick — first run primes from network, subsequent
        // runs read cached worktrees. Override with -Dkairo.dogfood.workdir=/path or
        // KAIRO_DOGFOOD_WORKDIR if you want a one-shot temp dir.
        Path tmp = workdir();
        Path cacheRoot = Files.createDirectories(tmp.resolve("cache"));
        Path dataRoot = Files.createDirectories(tmp.resolve("data"));

        var cache = new PluginCacheManager(cacheRoot);
        var fetchers = new SourceFetcherRegistry().register(new GitSubdirSourceFetcher(cache));
        var manager =
                new DefaultPluginManager(
                        new DefaultPluginRegistry(),
                        new PluginLoader(),
                        dataRoot,
                        ComponentRegistrar.noOp(),
                        fetchers);

        System.out.println("=== Kairo plugin dogfood: anthropics/claude-code @ main ===");
        System.out.println("(workdir: " + tmp + ")\n");

        for (String subdir : SUBDIRS) {
            System.out.println("→ " + subdir);
            try {
                PluginInstallation inst =
                        manager.install(
                                        new PluginSource.GitSubdir(REPO, "main", subdir),
                                        PluginScope.USER)
                                .block(Duration.ofMinutes(8));
                manager.enable(inst.id()).block(Duration.ofSeconds(30));

                PluginManifest manifest = new PluginLoader().load(inst.rootPath(), null);
                Map<String, Integer> counts = countByType(manifest.components());
                System.out.printf(
                        "  loaded as '%s' v%s  (id=%s)%n",
                        inst.metadata().name(), inst.metadata().version(), inst.id());
                if (counts.isEmpty()) {
                    System.out.println("  (no components)");
                } else {
                    counts.forEach((type, n) -> System.out.printf("    %-22s %d%n", type, n));
                }
            } catch (Exception e) {
                System.out.println("  FAILED: " + rootCause(e));
            }
            System.out.println();
        }

        System.out.printf("Manager has %d plugin(s) installed%n", manager.list().size());
        System.out.println("Done.");
    }

    private static Path workdir() throws java.io.IOException {
        String fromProp = System.getProperty("kairo.dogfood.workdir");
        String fromEnv = System.getenv("KAIRO_DOGFOOD_WORKDIR");
        String chosen = fromProp != null ? fromProp : fromEnv;
        if (chosen != null && !chosen.isBlank()) {
            return Files.createDirectories(Path.of(chosen));
        }
        return Files.createDirectories(
                Path.of(System.getProperty("java.io.tmpdir"), "kairo-dogfood-stable"));
    }

    private static Map<String, Integer> countByType(List<PluginComponent> components) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PluginComponent c : components) {
            counts.merge(c.getClass().getSimpleName(), 1, Integer::sum);
        }
        return counts;
    }

    private static String rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
