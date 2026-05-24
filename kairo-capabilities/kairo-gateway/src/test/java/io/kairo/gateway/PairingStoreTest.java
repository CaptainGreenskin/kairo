/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.gateway.session.PairingStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PairingStoreTest {

    @Test
    void inMemoryRoundTrip() {
        var s = new PairingStore();
        s.pair("telegram", "tg-user-1", "kairo-user-A");
        assertThat(s.lookup("telegram", "tg-user-1")).contains("kairo-user-A");
        assertThat(s.lookup("telegram", "tg-user-2")).isEmpty();
        assertThat(s.size()).isEqualTo(1);
    }

    @Test
    void unpairRemoves() {
        var s = new PairingStore();
        s.pair("x", "y", "z");
        assertThat(s.unpair("x", "y")).isTrue();
        assertThat(s.lookup("x", "y")).isEmpty();
        assertThat(s.unpair("x", "y")).isFalse();
    }

    @Test
    void persistsToDisk(@TempDir Path tmp) {
        Path file = tmp.resolve("pairings.json");
        var s1 = new PairingStore(file);
        s1.pair("feishu", "fs-1", "kairo-A");
        s1.pair("wecom", "wc-2", "kairo-B");
        var s2 = new PairingStore(file);
        assertThat(s2.lookup("feishu", "fs-1")).contains("kairo-A");
        assertThat(s2.lookup("wecom", "wc-2")).contains("kairo-B");
        assertThat(s2.size()).isEqualTo(2);
    }

    @Test
    void overwriteUpdatesValue() {
        var s = new PairingStore();
        s.pair("x", "y", "first");
        s.pair("x", "y", "second");
        assertThat(s.lookup("x", "y")).contains("second");
        assertThat(s.size()).isEqualTo(1);
    }
}
