/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.api.agent;

import io.kairo.api.Experimental;
import java.util.List;
import java.util.Optional;

/**
 * Catalog of {@link SubagentDefinition}s available at runtime. Plugins (and tests) register
 * subagents here; agent schedulers consume them.
 *
 * @apiNote Implementations must be thread-safe; concurrent {@code register}/{@code get} are
 *     expected during plugin enable/disable.
 * @since 1.2
 */
@Experimental("Subagent SPI — contract may change in v1.x")
public interface SubagentRegistry {

    /**
     * Registers a subagent. If a subagent with the same {@link SubagentDefinition#qualifiedName()}
     * is already present, the registration is rejected and {@link IllegalStateException} is thrown
     * — duplicate plugin contributions must be resolved by the caller (e.g. by changing namespace).
     */
    void register(SubagentDefinition definition);

    /** Removes a subagent by qualified name. Returns true if it was present. */
    boolean unregister(String qualifiedName);

    /** Looks up a subagent by qualified name. */
    Optional<SubagentDefinition> get(String qualifiedName);

    /** Snapshot of all registered subagents. */
    List<SubagentDefinition> list();

    /** Snapshot filtered by plugin namespace. */
    List<SubagentDefinition> listByNamespace(String namespace);

    /** Number of registered subagents. */
    int size();
}
