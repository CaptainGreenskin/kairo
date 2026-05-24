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
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LifecycleCuratorDaemonTest {

    private static final Duration STALE = Duration.ofDays(30);
    private static final Duration ARCHIVE = Duration.ofDays(90);

    private LifecycleCuratorDaemon newDaemon(InMemorySkillTelemetryStore store) {
        CuratorConfig cfg =
                new CuratorConfig(
                        Duration.ofDays(7),
                        Duration.ZERO, // no idle gate in tests
                        STALE,
                        ARCHIVE,
                        true,
                        false);
        return new LifecycleCuratorDaemon(store, CuratorIdleSignal.alwaysIdle(), cfg);
    }

    @Test
    void freshSkillStaysActive() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant now = Instant.now();
        store.recordUse("recent", now).block();

        LifecycleTransitionResult result = newDaemon(store).runOnce();

        assertThat(result.markedStale()).isEmpty();
        assertThat(result.archived()).isEmpty();
        assertThat(result.reactivated()).isEmpty();
        assertThat(store.get("recent").block().orElseThrow().state())
                .isEqualTo(SkillLifecycleState.ACTIVE);
    }

    @Test
    void unusedPast30DaysIsMarkedStale() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant old = Instant.now().minus(Duration.ofDays(40));
        store.recordUse("dusty", old).block();

        LifecycleTransitionResult result = newDaemon(store).runOnce();

        assertThat(result.markedStale()).containsExactly("dusty");
        assertThat(result.archived()).isEmpty();
        assertThat(store.get("dusty").block().orElseThrow().state())
                .isEqualTo(SkillLifecycleState.STALE);
    }

    @Test
    void unusedPast90DaysIsArchivedInOnePass() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant ancient = Instant.now().minus(Duration.ofDays(120));
        store.recordUse("forgotten", ancient).block();

        LifecycleTransitionResult result = newDaemon(store).runOnce();

        assertThat(result.archived()).containsExactly("forgotten");
        SkillTelemetry archived = store.get("forgotten").block().orElseThrow();
        assertThat(archived.state()).isEqualTo(SkillLifecycleState.ARCHIVED);
        assertThat(archived.archivedAt()).isNotNull();
    }

    @Test
    void reactivationHappensWhenStaleSkillSeesNewActivity() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant old = Instant.now().minus(Duration.ofDays(45));
        store.recordUse("comeback", old).block();
        store.setState("comeback", SkillLifecycleState.STALE, old).block();

        // New activity arrives.
        store.recordUse("comeback", Instant.now()).block();

        LifecycleTransitionResult result = newDaemon(store).runOnce();

        assertThat(result.reactivated()).containsExactly("comeback");
        assertThat(store.get("comeback").block().orElseThrow().state())
                .isEqualTo(SkillLifecycleState.ACTIVE);
    }

    @Test
    void pinnedSkillIsImmuneEvenWhenAncient() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant ancient = Instant.now().minus(Duration.ofDays(120));
        store.recordUse("treasure", ancient).block();
        store.setPinned("treasure", true, ancient).block();

        LifecycleTransitionResult result = newDaemon(store).runOnce();

        assertThat(result.archived()).isEmpty();
        assertThat(result.markedStale()).isEmpty();
        assertThat(result.skippedImmune()).containsExactly("treasure");
    }

    @Test
    void bundledAndHubProvenanceAreImmune() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant ancient = Instant.now().minus(Duration.ofDays(120));
        store.save(SkillTelemetry.initial("ships-with-app", SkillProvenance.BUNDLED, ancient))
                .block();
        store.save(SkillTelemetry.initial("plugin-installed", SkillProvenance.HUB, ancient))
                .block();

        LifecycleTransitionResult result = newDaemon(store).runOnce();

        assertThat(result.skippedImmune())
                .containsExactlyInAnyOrder("ships-with-app", "plugin-installed");
        assertThat(result.archived()).isEmpty();
    }

    @Test
    void disabledOrPausedShortCircuits() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant ancient = Instant.now().minus(Duration.ofDays(120));
        store.recordUse("forgotten", ancient).block();

        CuratorConfig disabled = CuratorConfig.defaults().withEnabled(false);
        LifecycleCuratorDaemon d1 =
                new LifecycleCuratorDaemon(store, CuratorIdleSignal.alwaysIdle(), disabled);
        assertThat(d1.runOnce().archived()).isEmpty();

        CuratorConfig paused = CuratorConfig.defaults().withPaused(true);
        LifecycleCuratorDaemon d2 =
                new LifecycleCuratorDaemon(store, CuratorIdleSignal.alwaysIdle(), paused);
        assertThat(d2.runOnce().archived()).isEmpty();

        // force=true overrides gates.
        assertThat(d2.runOnce(true).archived()).containsExactly("forgotten");
    }

    @Test
    void idleGateSkipsWhenHostIsBusy() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant ancient = Instant.now().minus(Duration.ofDays(120));
        store.recordUse("forgotten", ancient).block();

        CuratorConfig cfg = CuratorConfig.defaults().withIdleThreshold(Duration.ofMinutes(5));
        LifecycleCuratorDaemon daemon =
                new LifecycleCuratorDaemon(store, CuratorIdleSignal.neverIdle(), cfg);

        assertThat(daemon.runOnce().archived()).isEmpty();
    }

    @Test
    void neverActiveSkillUsesCreatedAtAsAnchor() {
        InMemorySkillTelemetryStore store = new InMemorySkillTelemetryStore();
        Instant fresh = Instant.now();
        store.save(SkillTelemetry.initial("just-born", SkillProvenance.AGENT_CREATED, fresh))
                .block();

        LifecycleTransitionResult result = newDaemon(store).runOnce();

        // Never used but created moments ago — should stay active.
        assertThat(store.get("just-born").block().orElseThrow().state())
                .isEqualTo(SkillLifecycleState.ACTIVE);
        assertThat(result.archived()).isEmpty();
    }
}
