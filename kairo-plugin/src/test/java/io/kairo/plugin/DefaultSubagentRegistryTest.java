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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.agent.SubagentDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSubagentRegistryTest {

    @Test
    void registerAndGetRoundTrip() {
        var reg = new DefaultSubagentRegistry();
        var def = new SubagentDefinition("rev", "desc", "prompt", List.of(), null, "plug");
        reg.register(def);
        assertThat(reg.get("plug:rev")).contains(def);
        assertThat(reg.size()).isEqualTo(1);
    }

    @Test
    void duplicateQualifiedNameThrows() {
        var reg = new DefaultSubagentRegistry();
        reg.register(new SubagentDefinition("rev", "d1", "p1", List.of(), null, "plug"));
        assertThatThrownBy(
                        () ->
                                reg.register(
                                        new SubagentDefinition(
                                                "rev", "d2", "p2", List.of(), null, "plug")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void unregisterReturnsTrueWhenPresent() {
        var reg = new DefaultSubagentRegistry();
        reg.register(new SubagentDefinition("a", "d", "p", List.of(), null, "ns"));
        assertThat(reg.unregister("ns:a")).isTrue();
        assertThat(reg.unregister("ns:a")).isFalse();
        assertThat(reg.size()).isZero();
    }

    @Test
    void listByNamespaceFilters() {
        var reg = new DefaultSubagentRegistry();
        reg.register(new SubagentDefinition("a", "d", "p", List.of(), null, "ns1"));
        reg.register(new SubagentDefinition("b", "d", "p", List.of(), null, "ns2"));
        reg.register(new SubagentDefinition("c", "d", "p", List.of(), null, "ns2"));
        assertThat(reg.listByNamespace("ns2")).hasSize(2);
        assertThat(reg.listByNamespace("nope")).isEmpty();
    }

    @Test
    void unnamespacedSubagentsAreListedByNullNamespace() {
        var reg = new DefaultSubagentRegistry();
        reg.register(new SubagentDefinition("plain", "d", "p", List.of(), null, null));
        assertThat(reg.listByNamespace(null)).hasSize(1);
    }

    @Test
    void differentNamespacesAllowSameLocalName() {
        var reg = new DefaultSubagentRegistry();
        reg.register(new SubagentDefinition("a", "d", "p", List.of(), null, "ns1"));
        reg.register(new SubagentDefinition("a", "d", "p", List.of(), null, "ns2"));
        assertThat(reg.size()).isEqualTo(2);
    }

    @Test
    void getReturnsEmptyForUnknownName() {
        var reg = new DefaultSubagentRegistry();
        assertThat(reg.get("nope:nada")).isEmpty();
    }
}
