/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.plugin;

import io.kairo.api.Experimental;
import java.util.List;
import java.util.Optional;

/**
 * In-memory directory of installed plugins. Persists nothing — that is {@code
 * EnabledPluginsStore}'s job. Used by {@link PluginManager} as its source of truth during a
 * runtime.
 *
 * @apiNote Implementations must be thread-safe.
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public interface PluginRegistry {

    /** Adds an installation. Replaces any existing installation with the same id. */
    void put(PluginInstallation installation);

    /** Removes the installation with the given id. Returns true if removed. */
    boolean remove(String id);

    /** Looks up an installation by id. */
    Optional<PluginInstallation> get(String id);

    /** All installations (snapshot). */
    List<PluginInstallation> list();

    /** Filtered by scope. */
    List<PluginInstallation> listByScope(PluginScope scope);

    /** Filtered to the enabled subset. */
    List<PluginInstallation> listEnabled();

    /** Number of installations. */
    int size();

    /** Removes all installations (for tests). */
    void clear();
}
