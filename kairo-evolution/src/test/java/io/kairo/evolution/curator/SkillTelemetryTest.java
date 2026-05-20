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

class SkillTelemetryTest {

    @Test
    void initialRecordHasZeroCountersAndActiveState() {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");
        SkillTelemetry t = SkillTelemetry.initial("foo", SkillProvenance.AGENT_CREATED, now);

        assertThat(t.skillName()).isEqualTo("foo");
        assertThat(t.state()).isEqualTo(SkillLifecycleState.ACTIVE);
        assertThat(t.provenance()).isEqualTo(SkillProvenance.AGENT_CREATED);
        assertThat(t.pinned()).isFalse();
        assertThat(t.useCount()).isZero();
        assertThat(t.viewCount()).isZero();
        assertThat(t.patchCount()).isZero();
        assertThat(t.createdAt()).isEqualTo(now);
        assertThat(t.lastActivityAt()).isNull();
        assertThat(t.activityCount()).isZero();
    }

    @Test
    void lastActivityAtReturnsNewestOfUseViewPatch() {
        Instant base = Instant.parse("2026-05-20T10:00:00Z");
        SkillTelemetry t =
                SkillTelemetry.initial("foo", SkillProvenance.AGENT_CREATED, base)
                        .withUse(base.plusSeconds(10))
                        .withView(base.plusSeconds(30))
                        .withPatch(base.plusSeconds(20));

        assertThat(t.lastActivityAt()).isEqualTo(base.plusSeconds(30));
        assertThat(t.activityCount()).isEqualTo(3);
    }

    @Test
    void pinnedOrNonAgentCreatedAreImmuneFromCurator() {
        Instant now = Instant.now();
        SkillTelemetry agent = SkillTelemetry.initial("a", SkillProvenance.AGENT_CREATED, now);
        SkillTelemetry bundled = SkillTelemetry.initial("b", SkillProvenance.BUNDLED, now);
        SkillTelemetry hub = SkillTelemetry.initial("h", SkillProvenance.HUB, now);
        SkillTelemetry manual = SkillTelemetry.initial("m", SkillProvenance.MANUAL, now);
        SkillTelemetry agentPinned = agent.withPinned(true);

        assertThat(agent.isCuratorImmune()).isFalse();
        assertThat(bundled.isCuratorImmune()).isTrue();
        assertThat(hub.isCuratorImmune()).isTrue();
        assertThat(manual.isCuratorImmune()).isTrue();
        assertThat(agentPinned.isCuratorImmune()).isTrue();
    }

    @Test
    void archivedRecordsAbsorbedIntoUmbrellaAndTimestamp() {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");
        SkillTelemetry t =
                SkillTelemetry.initial("foo", SkillProvenance.AGENT_CREATED, now)
                        .archived(now, "umbrella-x");

        assertThat(t.state()).isEqualTo(SkillLifecycleState.ARCHIVED);
        assertThat(t.archivedAt()).isEqualTo(now);
        assertThat(t.absorbedInto()).isEqualTo("umbrella-x");
    }

    @Test
    void mutatorsArePureAndDoNotShareState() {
        Instant t0 = Instant.parse("2026-05-20T10:00:00Z");
        SkillTelemetry original = SkillTelemetry.initial("foo", SkillProvenance.AGENT_CREATED, t0);
        SkillTelemetry afterUse = original.withUse(t0.plusSeconds(1));

        assertThat(original.useCount()).isZero();
        assertThat(afterUse.useCount()).isOne();
        assertThat(original.lastUsedAt()).isNull();
        assertThat(afterUse.lastUsedAt()).isEqualTo(t0.plusSeconds(1));
    }
}
