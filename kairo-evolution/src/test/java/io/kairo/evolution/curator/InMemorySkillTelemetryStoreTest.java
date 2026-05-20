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
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemorySkillTelemetryStoreTest {

    @Test
    void recordUseSeedsRecordOnFirstCall() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant now = Instant.parse("2026-05-20T10:00:00Z");

        SkillTelemetry first = store.recordUse("foo", now).block();
        assertThat(first).isNotNull();
        assertThat(first.useCount()).isOne();
        assertThat(first.provenance()).isEqualTo(SkillProvenance.AGENT_CREATED);

        SkillTelemetry second = store.recordUse("foo", now.plusSeconds(60)).block();
        assertThat(second).isNotNull();
        assertThat(second.useCount()).isEqualTo(2);
        assertThat(second.lastUsedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void pinAndArchiveRoundTrip() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant now = Instant.parse("2026-05-20T10:00:00Z");

        store.recordUse("foo", now).block();
        store.setPinned("foo", true, now).block();

        SkillTelemetry pinned = store.get("foo").block().orElseThrow();
        assertThat(pinned.pinned()).isTrue();

        store.archive("foo", "umbrella", now.plusSeconds(10)).block();
        SkillTelemetry archived = store.get("foo").block().orElseThrow();
        assertThat(archived.state()).isEqualTo(SkillLifecycleState.ARCHIVED);
        assertThat(archived.absorbedInto()).isEqualTo("umbrella");
    }

    @Test
    void listReturnsAllStoredRecords() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant now = Instant.now();
        store.recordUse("a", now).block();
        store.recordUse("b", now).block();
        store.recordView("c", now).block();

        assertThat(store.list().collectList().block()).hasSize(3);
    }
}
