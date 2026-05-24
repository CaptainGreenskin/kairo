/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.evolution.curator;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.SkillLifecycleState;
import io.kairo.api.evolution.SkillProvenance;
import io.kairo.api.evolution.SkillTelemetry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSkillTelemetryStoreTest {

    @Test
    void persistsAcrossReopens(@TempDir Path dir) {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");

        FileSkillTelemetryStore store = new FileSkillTelemetryStore(dir);
        store.recordUse("foo", now).block();
        store.recordView("foo", now.plusSeconds(5)).block();
        store.recordPatch("bar", now).block();
        store.setPinned("foo", true, now).block();

        assertThat(Files.isRegularFile(store.file())).isTrue();

        // Reopen — should see exactly what we persisted.
        FileSkillTelemetryStore reopened = new FileSkillTelemetryStore(dir);
        SkillTelemetry foo = reopened.get("foo").block().orElseThrow();
        assertThat(foo.useCount()).isOne();
        assertThat(foo.viewCount()).isOne();
        assertThat(foo.pinned()).isTrue();
        assertThat(foo.lastUsedAt()).isEqualTo(now);
        assertThat(foo.lastViewedAt()).isEqualTo(now.plusSeconds(5));

        SkillTelemetry bar = reopened.get("bar").block().orElseThrow();
        assertThat(bar.patchCount()).isOne();
    }

    @Test
    void archiveSetsStateAndAbsorbedIntoAtomically(@TempDir Path dir) {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");
        FileSkillTelemetryStore store = new FileSkillTelemetryStore(dir);
        store.recordUse("foo", now).block();
        store.archive("foo", "umbrella-x", now.plusSeconds(60)).block();

        SkillTelemetry archived = store.get("foo").block().orElseThrow();
        assertThat(archived.state()).isEqualTo(SkillLifecycleState.ARCHIVED);
        assertThat(archived.absorbedInto()).isEqualTo("umbrella-x");
        assertThat(archived.archivedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void missingDirectoryReturnsEmpty(@TempDir Path dir) {
        FileSkillTelemetryStore store = new FileSkillTelemetryStore(dir.resolve("absent"));
        assertThat(store.list().collectList().block()).isEmpty();
        assertThat(store.get("anything").block()).isEmpty();
    }

    @Test
    void provenanceIsPreservedAcrossReopens(@TempDir Path dir) {
        Instant now = Instant.now();
        FileSkillTelemetryStore store = new FileSkillTelemetryStore(dir);
        store.save(SkillTelemetry.initial("bundled-skill", SkillProvenance.BUNDLED, now)).block();

        FileSkillTelemetryStore reopened = new FileSkillTelemetryStore(dir);
        SkillTelemetry t = reopened.get("bundled-skill").block().orElseThrow();
        assertThat(t.provenance()).isEqualTo(SkillProvenance.BUNDLED);
        assertThat(t.isCuratorImmune()).isTrue();
    }
}
