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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.plugin.PluginInstallation;
import io.kairo.api.plugin.PluginMetadata;
import io.kairo.api.plugin.PluginScope;
import io.kairo.api.plugin.PluginSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyResolverTest {

    private final DependencyResolver resolver = new DependencyResolver();

    @Test
    void leafPluginEnableOrderIsItself() {
        var alpha = inst("alpha", "1.0.0", List.of(), false);
        var order = resolver.enableOrder(List.of(alpha), alpha.id());
        assertThat(order).containsExactly(alpha.id());
    }

    @Test
    void linearChainEnablesDependenciesFirst() {
        var alpha = inst("alpha", "1.0.0", List.of(), false);
        var beta = inst("beta", "1.0.0", List.of(dep("alpha", "1.0.0")), false);
        var gamma = inst("gamma", "1.0.0", List.of(dep("beta", "1.0.0")), false);
        var order = resolver.enableOrder(List.of(alpha, beta, gamma), gamma.id());
        assertThat(order).containsExactly(alpha.id(), beta.id(), gamma.id());
    }

    @Test
    void diamondDependenciesEachVisitedOnce() {
        // gamma depends on alpha + beta; both depend on root.
        var root = inst("root", "1.0.0", List.of(), false);
        var alpha = inst("alpha", "1.0.0", List.of(dep("root", "*")), false);
        var beta = inst("beta", "1.0.0", List.of(dep("root", "*")), false);
        var gamma = inst("gamma", "1.0.0", List.of(dep("alpha", "*"), dep("beta", "*")), false);

        var order = resolver.enableOrder(List.of(root, alpha, beta, gamma), gamma.id());
        assertThat(order).hasSize(4);
        assertThat(order.get(0)).isEqualTo(root.id());
        assertThat(order).endsWith(gamma.id());
        assertThat(order).contains(alpha.id(), beta.id());
    }

    @Test
    void missingDependencyIsReportedClearly() {
        var beta = inst("beta", "1.0.0", List.of(dep("alpha", "*")), false);
        assertThatThrownBy(() -> resolver.enableOrder(List.of(beta), beta.id()))
                .isInstanceOf(DependencyResolver.UnresolvableDependencyException.class)
                .matches(
                        e ->
                                ((DependencyResolver.UnresolvableDependencyException) e)
                                        .kind()
                                        .equals("missing"))
                .hasMessageContaining("'alpha'");
    }

    @Test
    void versionMismatchIsReported() {
        var alpha = inst("alpha", "1.5.0", List.of(), false);
        var beta = inst("beta", "1.0.0", List.of(dep("alpha", "^2.0.0")), false);
        assertThatThrownBy(() -> resolver.enableOrder(List.of(alpha, beta), beta.id()))
                .matches(
                        e ->
                                ((DependencyResolver.UnresolvableDependencyException) e)
                                        .kind()
                                        .equals("versionMismatch"))
                .hasMessageContaining("^2.0.0")
                .hasMessageContaining("1.5.0");
    }

    @Test
    void cycleIsDetected() {
        var a = inst("a", "1.0.0", List.of(dep("b", "*")), false);
        var b = inst("b", "1.0.0", List.of(dep("a", "*")), false);
        assertThatThrownBy(() -> resolver.enableOrder(List.of(a, b), a.id()))
                .matches(
                        e ->
                                ((DependencyResolver.UnresolvableDependencyException) e)
                                        .kind()
                                        .equals("cycle"));
    }

    @Test
    void duplicatePluginNameSurfacesAsResolverError() {
        var a1 = inst("a", "1.0.0", List.of(), false);
        var a2 = inst("a", "2.0.0", List.of(), false);
        assertThatThrownBy(() -> resolver.enableOrder(List.of(a1, a2), a1.id()))
                .matches(
                        e ->
                                ((DependencyResolver.UnresolvableDependencyException) e)
                                        .kind()
                                        .equals("duplicate"));
    }

    @Test
    void unknownTargetIsMissing() {
        var a = inst("a", "1.0.0", List.of(), false);
        assertThatThrownBy(() -> resolver.enableOrder(List.of(a), "nonexistent"))
                .matches(
                        e ->
                                ((DependencyResolver.UnresolvableDependencyException) e)
                                        .kind()
                                        .equals("missing"));
    }

    @Test
    void wildcardVersionAlwaysSatisfied() {
        var a = inst("a", "0.0.1", List.of(), false);
        var b = inst("b", "1.0.0", List.of(dep("a", "*")), false);
        var order = resolver.enableOrder(List.of(a, b), b.id());
        assertThat(order).containsExactly(a.id(), b.id());
    }

    @Test
    void disableCascadeSurfacesEnabledDependents() {
        var alpha = inst("alpha", "1.0.0", List.of(), true);
        var beta = inst("beta", "1.0.0", List.of(dep("alpha", "*")), true);
        var gamma = inst("gamma", "1.0.0", List.of(dep("beta", "*")), true);

        // Disabling alpha must cascade to beta and gamma.
        var order = resolver.disableCascade(List.of(alpha, beta, gamma), alpha.id());
        // The exact ordering of dependents is implementation-defined, but the target alpha must
        // be the very last one disabled.
        assertThat(order).hasSize(3).endsWith(alpha.id());
        assertThat(order).contains(beta.id(), gamma.id());
    }

    @Test
    void disableCascadeIgnoresAlreadyDisabledDependents() {
        var alpha = inst("alpha", "1.0.0", List.of(), true);
        var beta = inst("beta", "1.0.0", List.of(dep("alpha", "*")), false); // not enabled
        var order = resolver.disableCascade(List.of(alpha, beta), alpha.id());
        assertThat(order).containsExactly(alpha.id());
    }

    private static PluginInstallation inst(
            String name, String version, List<PluginMetadata.Dependency> deps, boolean enabled) {
        var meta = new PluginMetadata(name, version, null, null, null, null, List.of(), deps);
        return new PluginInstallation(
                "id:" + name + ":" + version,
                meta,
                new PluginSource.LocalPath(Path.of("/tmp")),
                PluginScope.USER,
                enabled,
                Path.of("/tmp"),
                Path.of("/tmp/data"),
                Instant.now());
    }

    private static PluginMetadata.Dependency dep(String name, String version) {
        return new PluginMetadata.Dependency(name, version);
    }
}
