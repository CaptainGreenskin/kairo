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
package io.kairo.spring.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillLifecycleState;
import io.kairo.api.evolution.SkillTrustLevel;
import io.kairo.evolution.curator.CuratorActionExecutor;
import io.kairo.evolution.curator.CuratorConfig;
import io.kairo.evolution.curator.CuratorIdleSignal;
import io.kairo.evolution.curator.InMemorySkillTelemetryStore;
import io.kairo.evolution.curator.LifecycleCuratorDaemon;
import io.kairo.evolution.curator.PrefixClusterCurator;
import io.kairo.evolution.curator.UmbrellaConsolidationPlanner;
import io.kairo.skill.InMemoryEvolvedSkillStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvolutionControllerTest {

    private static EvolvedSkill skill(String name, String body) {
        Instant now = Instant.now();
        return new EvolvedSkill(
                name,
                "1.0",
                "",
                body,
                "general",
                Set.of(),
                SkillTrustLevel.DRAFT,
                null,
                now,
                now,
                0L);
    }

    private EvolutionController newController() {
        InMemoryEvolvedSkillStore skills = new InMemoryEvolvedSkillStore();
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();
        CuratorConfig cfg =
                new CuratorConfig(
                        Duration.ofDays(7),
                        Duration.ZERO,
                        Duration.ofDays(30),
                        Duration.ofDays(90),
                        true,
                        false);
        LifecycleCuratorDaemon daemon =
                new LifecycleCuratorDaemon(telemetry, CuratorIdleSignal.alwaysIdle(), cfg);
        CuratorActionExecutor executor = new CuratorActionExecutor(skills, telemetry);
        UmbrellaConsolidationPlanner planner =
                new UmbrellaConsolidationPlanner(
                        skills,
                        telemetry,
                        daemon,
                        new PrefixClusterCurator(),
                        executor,
                        CuratorIdleSignal.alwaysIdle(),
                        Duration.ZERO);
        skills.save(skill("pr-umbrella", "x".repeat(500))).block();
        skills.save(skill("pr-fix-1", "narrow")).block();
        telemetry.recordUse("pr-umbrella", Instant.now()).block();
        telemetry.recordUse("pr-fix-1", Instant.now()).block();
        return new EvolutionController(skills, telemetry, daemon, planner);
    }

    @Test
    void listSkillsReturnsCatalogWithTelemetry() {
        EvolutionController c = newController();
        var views = c.listSkills().block();
        assertThat(views).hasSize(2);
        assertThat(views.stream().map(v -> v.skill().name()))
                .containsExactlyInAnyOrder("pr-umbrella", "pr-fix-1");
        assertThat(views.stream().allMatch(v -> v.telemetry() != null)).isTrue();
    }

    @Test
    void pinAndUnpinFlipTheFlag() {
        EvolutionController c = newController();
        var pinned = c.pin("pr-fix-1").block();
        assertThat(pinned.pinned()).isTrue();
        var unpinned = c.unpin("pr-fix-1").block();
        assertThat(unpinned.pinned()).isFalse();
    }

    @Test
    void archiveRemovesSkillAndMarksTelemetry() {
        EvolutionController c = newController();
        c.archive("pr-fix-1").block();
        var views = c.listSkills().block();
        // Underlying store removes the skill on archive, so the view list shrinks.
        assertThat(views.stream().map(v -> v.skill().name())).doesNotContain("pr-fix-1");
    }

    @Test
    void dryRunRunCuratorReportsActionsWithoutMutating() {
        EvolutionController c = newController();
        var dry = c.runCurator(true).block();
        assertThat(dry).isNotNull();
        assertThat(dry.dryRun()).isTrue();
        // PrefixClusterCurator does NOT propose merges for 1-sibling clusters,
        // but pr-umbrella + pr-fix-1 share prefix "pr" — 2 entries qualifies.
        assertThat(dry.consolidation().applied()).hasSize(1);
        // Mutations did NOT happen.
        var views = c.listSkills().block();
        assertThat(views.stream().map(v -> v.skill().name())).contains("pr-fix-1");
    }

    @Test
    void runLifecycleMarksStaleByItself() {
        EvolutionController c = newController();
        // Force a stale window.
        c.archive("pr-fix-1").block();
        var result = c.runLifecycle().block();
        assertThat(result).isNotNull();
        // pr-umbrella is fresh, pr-fix-1 was just archived — neither should transition again.
        assertThat(result.markedStale()).doesNotContain("pr-fix-1");
        assertThat(result.archived()).doesNotContain("pr-fix-1");
    }

    @Test
    void curatorRunResponseExposesFlattenedFields() {
        EvolutionController c = newController();
        var planResult = c.runCurator(true).block();
        var response = EvolutionController.CuratorRunResponse.from(planResult);
        assertThat(response.dryRun()).isTrue();
        assertThat(response.consolidation()).isNotNull();
        assertThat(response.totalChanged()).isEqualTo(planResult.totalChanged());
    }

    @Test
    void listIncludesArchivedTelemetryAfterCuratorRun() {
        EvolutionController c = newController();
        c.runCurator(false).block();
        // After applying the prefix cluster merge, pr-fix-1 is archived AND
        // deleted from the skill store. listSkills() only lists skills that are still
        // in the store, but telemetry persists.
        var views = c.listSkills().block();
        assertThat(views.stream().map(v -> v.skill().name())).contains("pr-umbrella");

        // Cross-check telemetry directly.
        var fixed = c.unpin("pr-fix-1").block();
        assertThat(fixed.state()).isEqualTo(SkillLifecycleState.ARCHIVED);
    }
}
