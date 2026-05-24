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

import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginRegistry;
import io.kairo.api.plugin.PluginScope;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory implementation of {@link PluginRegistry} backed by {@link
 * ConcurrentHashMap}. Used by {@link DefaultPluginManager} as its source of truth for the running
 * process. Persistence (writing back to settings.json) is the manager's responsibility, not this
 * registry's.
 */
public final class DefaultPluginRegistry implements PluginRegistry {

    private final ConcurrentMap<String, PluginInstallation> installations =
            new ConcurrentHashMap<>();

    @Override
    public void put(PluginInstallation installation) {
        installations.put(installation.id(), installation);
    }

    @Override
    public boolean remove(String id) {
        return installations.remove(id) != null;
    }

    @Override
    public Optional<PluginInstallation> get(String id) {
        return Optional.ofNullable(installations.get(id));
    }

    @Override
    public List<PluginInstallation> list() {
        return List.copyOf(installations.values());
    }

    @Override
    public List<PluginInstallation> listByScope(PluginScope scope) {
        return installations.values().stream().filter(p -> p.scope() == scope).toList();
    }

    @Override
    public List<PluginInstallation> listEnabled() {
        return installations.values().stream().filter(PluginInstallation::enabled).toList();
    }

    @Override
    public int size() {
        return installations.size();
    }

    @Override
    public void clear() {
        installations.clear();
    }
}
