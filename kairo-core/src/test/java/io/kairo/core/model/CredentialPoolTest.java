/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CredentialPoolTest {

    @Test
    void roundRobinRotation() {
        CredentialPool pool = new CredentialPool(List.of("k1", "k2", "k3"));
        String a = pool.next();
        String b = pool.next();
        String c = pool.next();
        String d = pool.next();

        assertThat(List.of(a, b, c)).containsExactlyInAnyOrder("k1", "k2", "k3");
        assertThat(d).isEqualTo(a);
    }

    @Test
    void skipsRateLimitedKey() {
        CredentialPool pool = new CredentialPool(List.of("k1", "k2"));
        pool.markRateLimited("k1", 60);

        assertThat(pool.next()).isEqualTo("k2");
        assertThat(pool.next()).isEqualTo("k2");
    }

    @Test
    void fallsBackWhenAllLimited() {
        CredentialPool pool = new CredentialPool(List.of("k1", "k2"));
        pool.markRateLimited("k1", 60);
        pool.markRateLimited("k2", 120);

        assertThat(pool.next()).isIn("k1", "k2");
    }

    @Test
    void emptyPoolThrows() {
        CredentialPool pool = new CredentialPool(List.of());
        assertThatThrownBy(pool::next).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sizeAndAvailable() {
        CredentialPool pool = new CredentialPool(List.of("a", "b", "c"));
        assertThat(pool.size()).isEqualTo(3);
        assertThat(pool.availableCount()).isEqualTo(3);

        pool.markRateLimited("a", 60);
        assertThat(pool.availableCount()).isEqualTo(2);
    }

    @Test
    void markFailedDisablesAfterThreshold() {
        CredentialPool pool = new CredentialPool(List.of("k1", "k2"), 3);
        pool.markFailed("k1");
        pool.markFailed("k1");
        assertThat(pool.availableCount()).isEqualTo(2);

        pool.markFailed("k1");
        assertThat(pool.availableCount()).isEqualTo(1);
    }

    @Test
    void markSuccessResetsFailCount() {
        CredentialPool pool = new CredentialPool(List.of("k1", "k2"), 3);
        pool.markFailed("k1");
        pool.markFailed("k1");
        pool.markSuccess("k1");
        pool.markFailed("k1");

        assertThat(pool.availableCount()).isEqualTo(2);
    }
}
