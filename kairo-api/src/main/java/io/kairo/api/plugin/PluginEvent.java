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
import java.time.Instant;

/**
 * Lifecycle event emitted by {@link PluginManager}.
 *
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public sealed interface PluginEvent {

    /** The installation this event refers to. */
    PluginInstallation installation();

    /** When the event occurred. */
    Instant timestamp();

    /** Plugin successfully installed (bytes resolved + manifest parsed + cached on disk). */
    record Installed(PluginInstallation installation, Instant timestamp) implements PluginEvent {}

    /** Plugin uninstalled (bytes removed from cache, settings entry deleted). */
    record Uninstalled(PluginInstallation installation, Instant timestamp) implements PluginEvent {}

    /** Plugin enabled — its components are now contributed to registries. */
    record Enabled(PluginInstallation installation, Instant timestamp) implements PluginEvent {}

    /** Plugin disabled — components removed from registries but install retained. */
    record Disabled(PluginInstallation installation, Instant timestamp) implements PluginEvent {}

    /** Plugin updated to a new version. */
    record Updated(PluginInstallation installation, String previousVersion, Instant timestamp)
            implements PluginEvent {}

    /**
     * Plugin enable failed; previously-registered components rolled back. Used to surface failure
     * causes to UI / audit log.
     */
    record EnableFailed(
            PluginInstallation installation,
            String failedComponent,
            String reason,
            Instant timestamp)
            implements PluginEvent {}
}
