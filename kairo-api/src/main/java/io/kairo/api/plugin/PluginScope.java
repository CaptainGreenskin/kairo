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

/**
 * Scope at which a plugin is installed and visible.
 *
 * <p>Resolution priority (highest first): {@link #LOCAL} &gt; {@link #PROJECT} &gt; {@link #USER}
 * &gt; {@link #MANAGED}. Higher-priority scopes shallow-merge over lower ones (nested objects are
 * replaced wholesale, arrays are replaced not concatenated).
 *
 * @since 1.2
 */
@Experimental("Plugin SPI — contract may change in v1.x")
public enum PluginScope {

    /** Installed for all users on the host. Settings file: {@code /etc/kairo/settings.json}. */
    MANAGED,

    /** Installed for the current OS user. Settings file: {@code ~/.kairo/settings.json}. */
    USER,

    /** Installed for the current project. Settings file: {@code <cwd>/.kairo/settings.json}. */
    PROJECT,

    /**
     * Installed locally for the current project (untracked, often gitignored). Settings file:
     * {@code <cwd>/.kairo/settings.local.json}.
     */
    LOCAL
}
