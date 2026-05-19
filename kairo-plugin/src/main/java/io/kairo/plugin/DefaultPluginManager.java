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

import io.kairo.api.plugin.PluginEvent;
import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginManager;
import io.kairo.api.plugin.PluginManifest;
import io.kairo.api.plugin.PluginRegistry;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.source.LocalPathSourceFetcher;
import io.kairo.plugin.source.SourceFetcherRegistry;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Phase A reference implementation of {@link PluginManager}. Supports only {@link
 * PluginSource.LocalPath} sources; remote sources arrive in Phase C. Component contributions to
 * Kairo registries (Tool / Skill / Hook / MCP / Bin) are not wired here — Phase B/C task adds the
 * binding. For now {@code enable} flips the {@code enabled} flag and emits the lifecycle event so
 * downstream code can subscribe.
 */
public final class DefaultPluginManager implements PluginManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginManager.class);

    private final PluginRegistry registry;
    private final PluginLoader loader;
    private final Path dataRoot;
    private final ComponentRegistrar componentRegistrar;
    private final SourceFetcherRegistry sourceFetchers;
    private final Sinks.Many<PluginEvent> events = Sinks.many().multicast().directBestEffort();

    /**
     * Convenience constructor — no real component binding (uses {@link ComponentRegistrar#noOp()})
     * and only the {@link LocalPathSourceFetcher} for sources. Mostly for tests.
     */
    public DefaultPluginManager(PluginRegistry registry, PluginLoader loader, Path dataRoot) {
        this(
                registry,
                loader,
                dataRoot,
                ComponentRegistrar.noOp(),
                new SourceFetcherRegistry().register(new LocalPathSourceFetcher()));
    }

    /** Convenience: keep custom registrar but default source fetchers to LocalPath only. */
    public DefaultPluginManager(
            PluginRegistry registry,
            PluginLoader loader,
            Path dataRoot,
            ComponentRegistrar componentRegistrar) {
        this(
                registry,
                loader,
                dataRoot,
                componentRegistrar,
                new SourceFetcherRegistry().register(new LocalPathSourceFetcher()));
    }

    /**
     * Full constructor — host application supplies its own component registrar + source fetchers.
     */
    public DefaultPluginManager(
            PluginRegistry registry,
            PluginLoader loader,
            Path dataRoot,
            ComponentRegistrar componentRegistrar,
            SourceFetcherRegistry sourceFetchers) {
        this.registry = registry;
        this.loader = loader;
        this.dataRoot = dataRoot;
        this.componentRegistrar =
                componentRegistrar == null ? ComponentRegistrar.noOp() : componentRegistrar;
        this.sourceFetchers =
                sourceFetchers == null
                        ? new SourceFetcherRegistry().register(new LocalPathSourceFetcher())
                        : sourceFetchers;
    }

    @Override
    public Mono<PluginInstallation> install(PluginSource source, PluginScope scope) {
        return sourceFetchers
                .fetch(source)
                .flatMap(
                        rootPath ->
                                Mono.fromCallable(
                                        () -> {
                                            PluginManifest manifest = loader.load(rootPath, null);
                                            String id = generateId(manifest, source);
                                            Path data =
                                                    dataRoot.resolve(manifest.metadata().name());
                                            PluginInstallation installation =
                                                    new PluginInstallation(
                                                            id,
                                                            manifest.metadata(),
                                                            source,
                                                            scope == null
                                                                    ? PluginScope.USER
                                                                    : scope,
                                                            false,
                                                            rootPath,
                                                            data,
                                                            Instant.now());
                                            registry.put(installation);
                                            emit(
                                                    new PluginEvent.Installed(
                                                            installation, Instant.now()));
                                            log.info(
                                                    "Installed plugin '{}' v{} (id={}, source={})",
                                                    installation.metadata().name(),
                                                    installation.metadata().version(),
                                                    id,
                                                    source.type());
                                            return installation;
                                        }));
    }

    private static String generateId(PluginManifest manifest, PluginSource source) {
        return source.type() + ":" + manifest.metadata().name() + ":" + UUID.randomUUID();
    }

    @Override
    public Mono<Void> uninstall(String id) {
        return Mono.fromRunnable(
                () -> {
                    PluginInstallation existing = registry.get(id).orElse(null);
                    if (existing == null) return;
                    if (existing.enabled()) {
                        registry.put(existing.withEnabled(false));
                        emit(new PluginEvent.Disabled(existing.withEnabled(false), Instant.now()));
                    }
                    registry.remove(id);
                    emit(new PluginEvent.Uninstalled(existing, Instant.now()));
                    log.info("Uninstalled plugin '{}' (id={})", existing.metadata().name(), id);
                });
    }

    @Override
    public Mono<Void> enable(String id) {
        return Mono.<Void>defer(
                () -> {
                    PluginInstallation existing =
                            registry.get(id)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "No plugin installed with id: " + id));
                    if (existing.enabled()) return Mono.<Void>empty();

                    // Re-load manifest from disk so component list reflects current state.
                    PluginManifest manifest;
                    try {
                        manifest = loader.load(existing.rootPath(), null);
                    } catch (Exception e) {
                        return Mono.<Void>error(e);
                    }

                    return componentRegistrar
                            .registerAll(id, manifest.components())
                            .then(
                                    Mono.<Void>fromRunnable(
                                            () -> {
                                                PluginInstallation enabled =
                                                        existing.withEnabled(true);
                                                registry.put(enabled);
                                                emit(
                                                        new PluginEvent.Enabled(
                                                                enabled, Instant.now()));
                                                log.info(
                                                        "Enabled plugin '{}' (id={}) with {}"
                                                                + " component(s)",
                                                        enabled.metadata().name(),
                                                        id,
                                                        manifest.components().size());
                                            }))
                            .onErrorResume(
                                    err -> {
                                        emit(
                                                new PluginEvent.EnableFailed(
                                                        existing,
                                                        "components",
                                                        err.getMessage(),
                                                        Instant.now()));
                                        return Mono.<Void>error(err);
                                    });
                });
    }

    @Override
    public Mono<Void> disable(String id) {
        return Mono.<Void>defer(
                () -> {
                    PluginInstallation existing = registry.get(id).orElse(null);
                    if (existing == null || !existing.enabled()) return Mono.<Void>empty();
                    return componentRegistrar
                            .unregisterAll(id)
                            .then(
                                    Mono.<Void>fromRunnable(
                                            () -> {
                                                PluginInstallation disabled =
                                                        existing.withEnabled(false);
                                                registry.put(disabled);
                                                emit(
                                                        new PluginEvent.Disabled(
                                                                disabled, Instant.now()));
                                                log.info(
                                                        "Disabled plugin '{}' (id={})",
                                                        disabled.metadata().name(),
                                                        id);
                                            }));
                });
    }

    @Override
    public Mono<PluginInstallation> update(String id) {
        return Mono.fromCallable(
                        () -> {
                            PluginInstallation existing =
                                    registry.get(id)
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalArgumentException(
                                                                    "No plugin installed with id: "
                                                                            + id));
                            String previousVersion = existing.metadata().version();
                            // v1.2: re-parse from disk; remote re-fetch is Phase C.
                            PluginManifest manifest = loader.load(existing.rootPath(), null);
                            PluginInstallation updated =
                                    new PluginInstallation(
                                            existing.id(),
                                            manifest.metadata(),
                                            existing.source(),
                                            existing.scope(),
                                            existing.enabled(),
                                            existing.rootPath(),
                                            existing.dataPath(),
                                            Instant.now());
                            registry.put(updated);
                            emit(new PluginEvent.Updated(updated, previousVersion, Instant.now()));
                            return updated;
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public List<PluginInstallation> list() {
        return registry.list();
    }

    @Override
    public Flux<PluginEvent> events() {
        return events.asFlux();
    }

    @Override
    public Mono<Void> reload() {
        // Phase A: nothing to reload (no real component registration yet); Phase B wires this.
        return Mono.empty();
    }

    private void emit(PluginEvent event) {
        events.tryEmitNext(event);
    }
}
