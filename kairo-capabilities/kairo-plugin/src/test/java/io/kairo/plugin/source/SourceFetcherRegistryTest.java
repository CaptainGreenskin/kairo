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
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SourceFetcherRegistryTest {

    @Test
    void findReturnsFirstSupportingFetcher() {
        var registry =
                new SourceFetcherRegistry()
                        .register(new LocalPathSourceFetcher())
                        .register(new StubFetcher("github"));

        var local = new PluginSource.LocalPath(Path.of("/tmp"));
        assertThat(registry.find(local)).isInstanceOf(LocalPathSourceFetcher.class);
    }

    @Test
    void findReturnsNullWhenNoFetcherSupports() {
        var registry = new SourceFetcherRegistry().register(new LocalPathSourceFetcher());
        assertThat(registry.find(new PluginSource.GitHub("a/b", null, null))).isNull();
    }

    @Test
    void fetchDispatchesOrErrorsWithUOE() {
        var registry = new SourceFetcherRegistry().register(new LocalPathSourceFetcher());
        assertThatThrownBy(
                        () ->
                                registry.fetch(new PluginSource.GitHub("a/b", null, null))
                                        .block(Duration.ofSeconds(2)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("github");
    }

    @Test
    void registerOrderDeterminesPriority() {
        var first = new StubFetcher("github");
        var second = new StubFetcher("github");
        var registry = new SourceFetcherRegistry().register(first).register(second);
        assertThat(registry.find(new PluginSource.GitHub("a/b", null, null))).isSameAs(first);
    }

    @Test
    void listSnapshotIsImmutable() {
        var registry = new SourceFetcherRegistry().register(new LocalPathSourceFetcher());
        var snapshot = registry.list();
        registry.register(new StubFetcher("npm"));
        assertThat(snapshot).hasSize(1);
    }

    /** Test fetcher that pretends to support one source kind. */
    private record StubFetcher(String kind) implements PluginSourceFetcher {
        @Override
        public boolean supports(PluginSource source) {
            return source.type().equals(kind);
        }

        @Override
        public Mono<Path> fetch(PluginSource source) {
            return Mono.just(Path.of("/stub"));
        }
    }
}
