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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

/**
 * A {@link ComponentRegistrar} that does no real binding but does track registration counts so
 * tests / diagnostics can verify the lifecycle was driven correctly.
 */
final class NoOpComponentRegistrar implements ComponentRegistrar {

    private final ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> registerAll(String pluginId, List<PluginComponent> components) {
        if (components == null || components.isEmpty()) return Mono.empty();
        counts.put(pluginId, components.size());
        return Mono.empty();
    }

    @Override
    public Mono<Void> unregisterAll(String pluginId) {
        counts.remove(pluginId);
        return Mono.empty();
    }

    @Override
    public int registeredCount(String pluginId) {
        return counts.getOrDefault(pluginId, 0);
    }
}
