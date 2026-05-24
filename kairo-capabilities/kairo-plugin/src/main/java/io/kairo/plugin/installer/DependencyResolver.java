/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.installer;

import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginMetadata;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes the enable order for a target plugin and its transitive dependencies, plus the
 * cascade-disable order when a plugin is being disabled.
 *
 * <p>Dependencies live in {@link PluginMetadata#dependencies()} as {@code (name, version)} pairs.
 * Resolution is by {@link PluginMetadata#name()} (not by installation id) — there must be at most
 * one installation per plugin name in the registry, otherwise the resolver throws.
 *
 * <p>Three failure modes are surfaced as {@link UnresolvableDependencyException}:
 *
 * <ul>
 *   <li>{@code missing} — a declared dependency has no installation
 *   <li>{@code versionMismatch} — installation exists but its version does not satisfy the
 *       constraint
 *   <li>{@code cycle} — A → B → A
 * </ul>
 *
 * <p>This class is pure: no side effects, no IO. It only inspects the registry snapshot it is
 * given.
 */
public final class DependencyResolver {

    /**
     * Returns plugin ids in enable order (dependencies first, target last).
     *
     * @param installations all currently-installed plugins (any scope, enabled or not)
     * @param targetId the id of the plugin being enabled
     */
    public List<String> enableOrder(List<PluginInstallation> installations, String targetId) {
        Map<String, PluginInstallation> byId = indexById(installations);
        Map<String, PluginInstallation> byName = indexByName(installations);
        PluginInstallation target = byId.get(targetId);
        if (target == null) {
            throw new UnresolvableDependencyException(
                    "missing", "No plugin installed with id: " + targetId);
        }

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        Set<String> visiting = new HashSet<>();
        visit(target, byName, ordered, visiting);
        return List.copyOf(ordered);
    }

    /**
     * Returns plugin ids in disable order (dependents first, target last) given the current set of
     * enabled installations.
     */
    public List<String> disableCascade(List<PluginInstallation> installations, String targetId) {
        Map<String, PluginInstallation> byId = indexById(installations);
        Map<String, PluginInstallation> byName = indexByName(installations);
        PluginInstallation target = byId.get(targetId);
        if (target == null) return List.of();

        // Find every enabled plugin whose declared dependencies include the target's name.
        LinkedHashSet<String> dependents = new LinkedHashSet<>();
        collectDependents(target.metadata().name(), installations, byName, dependents);
        List<String> reverseOrder = new ArrayList<>(dependents);
        java.util.Collections.reverse(reverseOrder); // disable leaves first
        // The target itself is the final disable.
        if (!reverseOrder.contains(targetId)) reverseOrder.add(targetId);
        return List.copyOf(reverseOrder);
    }

    private void visit(
            PluginInstallation node,
            Map<String, PluginInstallation> byName,
            LinkedHashSet<String> ordered,
            Set<String> visiting) {
        if (ordered.contains(node.id())) return;
        if (visiting.contains(node.id())) {
            throw new UnresolvableDependencyException(
                    "cycle", "Dependency cycle involving plugin '" + node.metadata().name() + "'");
        }
        visiting.add(node.id());
        for (PluginMetadata.Dependency dep : node.metadata().dependencies()) {
            String depName = dep.name();
            String constraint = dep.version();
            PluginInstallation depInstallation = byName.get(depName);
            if (depInstallation == null) {
                throw new UnresolvableDependencyException(
                        "missing",
                        "Plugin '"
                                + node.metadata().name()
                                + "' depends on '"
                                + depName
                                + "' but no plugin with that name is installed");
            }
            if (constraint != null
                    && !constraint.isBlank()
                    && !SemVer.satisfies(depInstallation.metadata().version(), constraint)) {
                throw new UnresolvableDependencyException(
                        "versionMismatch",
                        "Plugin '"
                                + node.metadata().name()
                                + "' requires '"
                                + depName
                                + "' "
                                + constraint
                                + ", but installed version is "
                                + depInstallation.metadata().version());
            }
            visit(depInstallation, byName, ordered, visiting);
        }
        visiting.remove(node.id());
        ordered.add(node.id());
    }

    private void collectDependents(
            String targetName,
            List<PluginInstallation> installations,
            Map<String, PluginInstallation> byName,
            LinkedHashSet<String> dependents) {
        boolean grew = true;
        Set<String> targetNames = new HashSet<>();
        targetNames.add(targetName);
        while (grew) {
            grew = false;
            for (PluginInstallation inst : installations) {
                if (!inst.enabled()) continue;
                if (dependents.contains(inst.id())) continue;
                for (PluginMetadata.Dependency d : inst.metadata().dependencies()) {
                    if (targetNames.contains(d.name())) {
                        dependents.add(inst.id());
                        targetNames.add(inst.metadata().name());
                        grew = true;
                        break;
                    }
                }
            }
        }
    }

    private Map<String, PluginInstallation> indexById(List<PluginInstallation> installations) {
        Map<String, PluginInstallation> out = new LinkedHashMap<>();
        for (PluginInstallation i : installations) out.put(i.id(), i);
        return out;
    }

    private Map<String, PluginInstallation> indexByName(List<PluginInstallation> installations) {
        Map<String, PluginInstallation> out = new LinkedHashMap<>();
        for (PluginInstallation i : installations) {
            String name = i.metadata().name();
            PluginInstallation prev = out.put(name, i);
            if (prev != null) {
                throw new UnresolvableDependencyException(
                        "duplicate",
                        "Multiple installations share the plugin name '"
                                + name
                                + "': "
                                + prev.id()
                                + " and "
                                + i.id());
            }
        }
        return out;
    }

    /** Thrown when a dependency graph cannot be resolved. */
    public static final class UnresolvableDependencyException extends RuntimeException {
        private final String kind;

        public UnresolvableDependencyException(String kind, String message) {
            super(message);
            this.kind = kind;
        }

        /**
         * Discriminator: {@code "missing"}, {@code "versionMismatch"}, {@code "cycle"}, or {@code
         * "duplicate"}.
         */
        public String kind() {
            return kind;
        }
    }
}
