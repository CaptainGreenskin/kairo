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
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation reserved for the Java-native plugin contribution path. Not loaded by v1.2.
 *
 * <p>In v1.2, all plugins are file-format plugins (Claude Code compatible directories). v1.3 will
 * activate this annotation as an additional discovery mechanism alongside the file-format loader,
 * allowing in-process Java code to act as a Plugin without a directory layout. The annotation is
 * defined now so that early adopters can opt in without depending on internal types later.
 *
 * @since 1.2
 */
@Experimental("Reserved for v1.3 — Java-native plugin path; not active in v1.2")
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface PluginContribution {

    /** Stable plugin id; same conventions as {@link PluginInstallation#id()}. */
    String id();

    /** Plugin version. */
    String version();

    /** Optional description. */
    String description() default "";
}
