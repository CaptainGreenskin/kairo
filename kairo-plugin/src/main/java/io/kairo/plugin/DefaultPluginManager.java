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
    private final Sinks.Many<PluginEvent> events = Sinks.many().multicast().directBestEffort();

    public DefaultPluginManager(PluginRegistry registry, PluginLoader loader, Path dataRoot) {
        this.registry = registry;
        this.loader = loader;
        this.dataRoot = dataRoot;
    }

    @Override
    public Mono<PluginInstallation> install(PluginSource source, PluginScope scope) {
        return Mono.fromCallable(
                        () -> {
                            if (!(source instanceof PluginSource.LocalPath local)) {
                                throw new UnsupportedOperationException(
                                        "Phase A only supports LocalPath sources; got "
                                                + source.type());
                            }
                            PluginManifest manifest = loader.load(local.path(), null);
                            String id =
                                    "local:" + manifest.metadata().name() + ":" + UUID.randomUUID();
                            Path data = dataRoot.resolve(manifest.metadata().name());
                            PluginInstallation installation =
                                    new PluginInstallation(
                                            id,
                                            manifest.metadata(),
                                            source,
                                            scope == null ? PluginScope.USER : scope,
                                            false,
                                            local.path(),
                                            data,
                                            Instant.now());
                            registry.put(installation);
                            emit(new PluginEvent.Installed(installation, Instant.now()));
                            log.info(
                                    "Installed plugin '{}' v{} (id={})",
                                    installation.metadata().name(),
                                    installation.metadata().version(),
                                    id);
                            return installation;
                        })
                .subscribeOn(Schedulers.boundedElastic());
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
        return Mono.fromRunnable(
                () -> {
                    PluginInstallation existing =
                            registry.get(id)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "No plugin installed with id: " + id));
                    if (existing.enabled()) return;
                    PluginInstallation enabled = existing.withEnabled(true);
                    registry.put(enabled);
                    emit(new PluginEvent.Enabled(enabled, Instant.now()));
                    log.info("Enabled plugin '{}' (id={})", enabled.metadata().name(), id);
                });
    }

    @Override
    public Mono<Void> disable(String id) {
        return Mono.fromRunnable(
                () -> {
                    PluginInstallation existing = registry.get(id).orElse(null);
                    if (existing == null || !existing.enabled()) return;
                    PluginInstallation disabled = existing.withEnabled(false);
                    registry.put(disabled);
                    emit(new PluginEvent.Disabled(disabled, Instant.now()));
                    log.info("Disabled plugin '{}' (id={})", disabled.metadata().name(), id);
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
