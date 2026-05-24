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

import org.junit.jupiter.api.Test;

class SemVerTest {

    @Test
    void parsesBasicTriple() {
        var v = SemVer.parse("1.2.3");
        assertThat(v.major()).isEqualTo(1);
        assertThat(v.minor()).isEqualTo(2);
        assertThat(v.patch()).isEqualTo(3);
        assertThat(v.preRelease()).isNull();
    }

    @Test
    void parsesPreReleaseSuffix() {
        var v = SemVer.parse("1.2.3-alpha.1");
        assertThat(v.preRelease()).isEqualTo("alpha.1");
    }

    @Test
    void parsesAndIgnoresBuildMetadata() {
        var v = SemVer.parse("1.2.3+build.42");
        assertThat(v.major()).isEqualTo(1);
        assertThat(v.preRelease()).isNull();
    }

    @Test
    void rejectsNonSemverStrings() {
        assertThatThrownBy(() -> SemVer.parse("1.2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemVer.parse("v1.2.3"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SemVer.parse("1.x.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compareToOrdersByMajorMinorPatch() {
        assertThat(SemVer.parse("1.2.3").compareTo(SemVer.parse("1.2.4"))).isNegative();
        assertThat(SemVer.parse("2.0.0").compareTo(SemVer.parse("1.99.99"))).isPositive();
        assertThat(SemVer.parse("1.2.3").compareTo(SemVer.parse("1.2.3"))).isZero();
    }

    @Test
    void preReleaseSortsBeforeRelease() {
        // Per semver: 1.0.0-alpha < 1.0.0
        assertThat(SemVer.parse("1.0.0-alpha").compareTo(SemVer.parse("1.0.0"))).isNegative();
        assertThat(SemVer.parse("1.0.0").compareTo(SemVer.parse("1.0.0-rc1"))).isPositive();
    }

    @Test
    void wildcardConstraintAcceptsAnything() {
        assertThat(SemVer.satisfies("1.2.3", "*")).isTrue();
        assertThat(SemVer.satisfies("99.99.99", "")).isTrue();
        assertThat(SemVer.satisfies("0.0.0", null)).isTrue();
    }

    @Test
    void exactConstraintRequiresExactMatch() {
        assertThat(SemVer.satisfies("1.2.3", "1.2.3")).isTrue();
        assertThat(SemVer.satisfies("1.2.4", "1.2.3")).isFalse();
    }

    @Test
    void caretAllowsCompatibleUpgrades() {
        // ^1.2.3 means [1.2.3, 2.0.0)
        assertThat(SemVer.satisfies("1.2.3", "^1.2.3")).isTrue();
        assertThat(SemVer.satisfies("1.5.0", "^1.2.3")).isTrue();
        assertThat(SemVer.satisfies("1.99.99", "^1.2.3")).isTrue();
        assertThat(SemVer.satisfies("2.0.0", "^1.2.3")).isFalse();
        assertThat(SemVer.satisfies("1.2.2", "^1.2.3")).isFalse();
    }

    @Test
    void caretOnZeroXLocksToMinor() {
        // ^0.2.3 means [0.2.3, 0.3.0) — npm 0.x semantics
        assertThat(SemVer.satisfies("0.2.3", "^0.2.3")).isTrue();
        assertThat(SemVer.satisfies("0.2.99", "^0.2.3")).isTrue();
        assertThat(SemVer.satisfies("0.3.0", "^0.2.3")).isFalse();
    }

    @Test
    void caretOnZeroZeroXLocksToPatch() {
        // ^0.0.3 means [0.0.3, 0.0.4)
        assertThat(SemVer.satisfies("0.0.3", "^0.0.3")).isTrue();
        assertThat(SemVer.satisfies("0.0.4", "^0.0.3")).isFalse();
    }

    @Test
    void tildeAllowsPatchUpgrades() {
        // ~1.2.3 means [1.2.3, 1.3.0)
        assertThat(SemVer.satisfies("1.2.3", "~1.2.3")).isTrue();
        assertThat(SemVer.satisfies("1.2.99", "~1.2.3")).isTrue();
        assertThat(SemVer.satisfies("1.3.0", "~1.2.3")).isFalse();
    }

    @Test
    void rangeOperators() {
        assertThat(SemVer.satisfies("2.0.0", ">=1.2.3")).isTrue();
        assertThat(SemVer.satisfies("1.0.0", ">=1.2.3")).isFalse();
        assertThat(SemVer.satisfies("1.0.0", "<2.0.0")).isTrue();
        assertThat(SemVer.satisfies("2.0.0", "<2.0.0")).isFalse();
        assertThat(SemVer.satisfies("1.0.0", ">0.9.0")).isTrue();
        assertThat(SemVer.satisfies("0.5.0", "<=1.0.0")).isTrue();
    }

    @Test
    void invalidVersionFailsConstraintCheck() {
        assertThat(SemVer.satisfies("not-a-version", "1.0.0")).isFalse();
    }

    @Test
    void toStringRoundTripsBasicVersion() {
        assertThat(SemVer.parse("1.2.3").toString()).isEqualTo("1.2.3");
        assertThat(SemVer.parse("1.2.3-alpha.1").toString()).isEqualTo("1.2.3-alpha.1");
    }
}
