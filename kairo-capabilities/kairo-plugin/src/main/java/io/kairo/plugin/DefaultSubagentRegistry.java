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

import io.kairo.api.agent.SubagentDefinition;
import io.kairo.api.agent.SubagentRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory implementation of {@link SubagentRegistry}.
 *
 * <p>Subagents are keyed by {@link SubagentDefinition#qualifiedName()} (i.e. {@code
 * <namespace>:<name>} when namespaced, otherwise the bare name). Re-registering an existing
 * qualified name throws — duplicate plugin contributions need to be resolved by changing namespace
 * at the contribution site.
 */
public final class DefaultSubagentRegistry implements SubagentRegistry {

    private final ConcurrentMap<String, SubagentDefinition> byQualifiedName =
            new ConcurrentHashMap<>();

    @Override
    public void register(SubagentDefinition definition) {
        String key = definition.qualifiedName();
        SubagentDefinition prev = byQualifiedName.putIfAbsent(key, definition);
        if (prev != null) {
            throw new IllegalStateException(
                    "Subagent '"
                            + key
                            + "' already registered (existing="
                            + prev
                            + ", attempted="
                            + definition
                            + ")");
        }
    }

    @Override
    public boolean unregister(String qualifiedName) {
        return byQualifiedName.remove(qualifiedName) != null;
    }

    @Override
    public Optional<SubagentDefinition> get(String qualifiedName) {
        return Optional.ofNullable(byQualifiedName.get(qualifiedName));
    }

    @Override
    public List<SubagentDefinition> list() {
        return List.copyOf(byQualifiedName.values());
    }

    @Override
    public List<SubagentDefinition> listByNamespace(String namespace) {
        return byQualifiedName.values().stream()
                .filter(
                        s ->
                                namespace == null
                                        ? (s.namespace() == null)
                                        : namespace.equals(s.namespace()))
                .toList();
    }

    @Override
    public int size() {
        return byQualifiedName.size();
    }
}
