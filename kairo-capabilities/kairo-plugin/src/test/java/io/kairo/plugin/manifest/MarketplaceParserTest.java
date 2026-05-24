/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.plugin.PluginSource;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketplaceParserTest {

    private final MarketplaceParser parser = new MarketplaceParser();

    @Test
    void parsesMinimalCatalog(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        {
                          "name": "official",
                          "owner": { "name": "anthropic" },
                          "plugins": []
                        }
                        """);
        var catalog = parser.parse(file);
        assertThat(catalog.name()).isEqualTo("official");
        assertThat(catalog.ownerName()).isEqualTo("anthropic");
        assertThat(catalog.plugins()).isEmpty();
        assertThat(catalog.trustLevel()).isEqualTo("unknown"); // default
    }

    @Test
    void ownerCanBeAStringShorthand(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        { "name": "x", "owner": "alice", "plugins": [] }
                        """);
        assertThat(parser.parse(file).ownerName()).isEqualTo("alice");
    }

    @Test
    void parsesAllFiveSourceTypes(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        {
                          "name": "many",
                          "owner": { "name": "test" },
                          "plugins": [
                            { "name": "p-path", "source": "./local/p" },
                            { "name": "p-github", "source": { "github": "owner/repo", "ref": "v1.0" } },
                            { "name": "p-subdir", "source": { "git-subdir": "https://x.git", "ref": "main", "subdir": "p/x" } },
                            { "name": "p-url", "source": { "url": "https://my.git", "ref": "trunk" } },
                            { "name": "p-npm", "source": { "npm": "@scope/pkg", "version": "1.2.3" } }
                          ]
                        }
                        """);
        var catalog = parser.parse(file);
        assertThat(catalog.plugins()).hasSize(5);

        var byName =
                catalog.plugins().stream()
                        .collect(java.util.stream.Collectors.toMap(e -> e.name(), e -> e.source()));

        assertThat(byName.get("p-path")).isInstanceOf(PluginSource.LocalPath.class);
        assertThat(((PluginSource.LocalPath) byName.get("p-path")).path().toString())
                .endsWith("local/p");

        var gh = (PluginSource.GitHub) byName.get("p-github");
        assertThat(gh.ownerRepo()).isEqualTo("owner/repo");
        assertThat(gh.ref()).isEqualTo("v1.0");

        var sub = (PluginSource.GitSubdir) byName.get("p-subdir");
        assertThat(sub.url()).isEqualTo("https://x.git");
        assertThat(sub.subdir()).isEqualTo("p/x");

        var url = (PluginSource.GitUrl) byName.get("p-url");
        assertThat(url.url()).isEqualTo("https://my.git");
        assertThat(url.ref()).isEqualTo("trunk");

        var npm = (PluginSource.Npm) byName.get("p-npm");
        assertThat(npm.packageName()).isEqualTo("@scope/pkg");
        assertThat(npm.version()).isEqualTo("1.2.3");
    }

    @Test
    void relativeStringPathIsResolvedAgainstMarketplaceFile(@TempDir Path tmp) throws Exception {
        Path subdir = Files.createDirectories(tmp.resolve("nested"));
        Path file =
                writeFile(
                        subdir,
                        "marketplace.json",
                        """
                        { "name": "x", "owner": "y", "plugins": [
                          { "name": "rel", "source": "./pkg" }
                        ] }
                        """);
        var catalog = parser.parse(file);
        var lp = (PluginSource.LocalPath) catalog.plugins().get(0).source();
        assertThat(lp.path().toString()).startsWith(subdir.toString());
        assertThat(lp.path().toString()).endsWith("pkg");
    }

    @Test
    void rejectsMissingName(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        { "owner": "x", "plugins": [] }
                        """);
        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsMissingOwner(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        { "name": "x", "plugins": [] }
                        """);
        assertThatThrownBy(() -> parser.parse(file)).hasMessageContaining("owner");
    }

    @Test
    void unknownSourceShapeIsSkippedNotError(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        { "name": "x", "owner": "y", "plugins": [
                          { "name": "alien", "source": { "weird": "stuff" } },
                          { "name": "ok", "source": { "github": "a/b", "ref": "main" } }
                        ] }
                        """);
        var catalog = parser.parse(file);
        assertThat(catalog.plugins()).hasSize(1);
        assertThat(catalog.plugins().get(0).name()).isEqualTo("ok");
    }

    @Test
    void preservesOptionalDescriptionAndVersion(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        { "name": "x", "owner": "y", "plugins": [
                          { "name": "p", "source": "./p", "description": "the p plugin", "version": "1.0.0" }
                        ] }
                        """);
        var entry = parser.parse(file).plugins().get(0);
        assertThat(entry.description()).isEqualTo("the p plugin");
        assertThat(entry.version()).isEqualTo("1.0.0");
    }

    @Test
    void trustLevelCustomValueIsPreserved(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        { "name": "x", "owner": "y", "trustLevel": "official", "plugins": [] }
                        """);
        assertThat(parser.parse(file).trustLevel()).isEqualTo("official");
    }

    @Test
    void gitSubdirWithoutSubdirFieldIsSkipped(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        { "name": "x", "owner": "y", "plugins": [
                          { "name": "bad", "source": { "git-subdir": "https://x", "ref": "main" } }
                        ] }
                        """);
        assertThat(parser.parse(file).plugins()).isEmpty();
    }

    @Test
    void explicitPathObjectIsAccepted(@TempDir Path tmp) throws Exception {
        Path file =
                writeFile(
                        tmp,
                        "marketplace.json",
                        """
                        { "name": "x", "owner": "y", "plugins": [
                          { "name": "p", "source": { "path": "/abs/p" } }
                        ] }
                        """);
        var lp = (PluginSource.LocalPath) parser.parse(file).plugins().get(0).source();
        assertThat(lp.path()).isEqualTo(Path.of("/abs/p"));
    }

    private static Path writeFile(Path dir, String name, String content)
            throws java.io.IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
