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

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillProvenance;
import io.kairo.api.evolution.SkillTelemetry;
import io.kairo.api.evolution.SkillTrustLevel;
import io.kairo.evolution.FileEvolvedSkillStore;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UmbrellaConsolidationPlannerTest {

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

    private UmbrellaConsolidationPlanner newPlanner(
            FileEvolvedSkillStore skills, InMemorySkillTelemetryStore telemetry) {
        LifecycleCuratorDaemon daemon = new LifecycleCuratorDaemon(telemetry);
        CuratorActionExecutor executor = new CuratorActionExecutor(skills, telemetry);
        LlmSkillCurator curator = new PrefixClusterCurator();
        return new UmbrellaConsolidationPlanner(
                skills,
                telemetry,
                daemon,
                curator,
                executor,
                CuratorIdleSignal.alwaysIdle(),
                Duration.ZERO);
    }

    @Test
    void prefixClusterEndToEndProducesMergeAndArchivesSibling(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();

        skills.save(skill("pr-umbrella", "x".repeat(500))).block();
        skills.save(skill("pr-fix-1", "narrow")).block();
        skills.save(skill("pr-fix-2", "narrow")).block();
        telemetry.recordUse("pr-umbrella", Instant.now()).block();
        telemetry.recordUse("pr-fix-1", Instant.now()).block();
        telemetry.recordUse("pr-fix-2", Instant.now()).block();

        UmbrellaConsolidationPlanner.PlanResult result =
                newPlanner(skills, telemetry).runOnce().block();

        assertThat(result).isNotNull();
        assertThat(result.dryRun()).isFalse();
        assertThat(result.consolidation().applied()).hasSize(1);
        assertThat(skills.get("pr-fix-1").block()).isEmpty();
        assertThat(skills.get("pr-fix-2").block()).isEmpty();
        assertThat(skills.get("pr-umbrella").block()).isPresent();
    }

    @Test
    void dryRunSkipsLifecycleAndMutationsButReportsActions(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();

        skills.save(skill("pr-umbrella", "x".repeat(500))).block();
        skills.save(skill("pr-fix-1", "narrow")).block();
        telemetry.recordUse("pr-umbrella", Instant.now()).block();
        telemetry.recordUse("pr-fix-1", Instant.now()).block();

        UmbrellaConsolidationPlanner.PlanResult result =
                newPlanner(skills, telemetry).dryRun().block();

        assertThat(result).isNotNull();
        assertThat(result.dryRun()).isTrue();
        assertThat(result.lifecycle()).isNull();
        assertThat(result.consolidation().applied()).hasSize(1);
        // Mutations did NOT happen.
        assertThat(skills.get("pr-fix-1").block()).isPresent();
    }

    @Test
    void archivedSkillsAreExcludedFromCatalog(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();
        skills.save(skill("a", "x")).block();
        telemetry
                .save(
                        SkillTelemetry.initial("a", SkillProvenance.AGENT_CREATED, Instant.now())
                                .archived(Instant.now(), null))
                .block();

        UmbrellaConsolidationPlanner.PlanResult result =
                newPlanner(skills, telemetry).dryRun().block();

        assertThat(result.consolidation().applied()).isEmpty();
    }

    @Test
    void idleGateSkipsPlannerWhenHostIsBusy(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();
        skills.save(skill("pr-a", "x")).block();
        skills.save(skill("pr-b", "x")).block();

        LifecycleCuratorDaemon daemon = new LifecycleCuratorDaemon(telemetry);
        CuratorActionExecutor executor = new CuratorActionExecutor(skills, telemetry);
        UmbrellaConsolidationPlanner planner =
                new UmbrellaConsolidationPlanner(
                        skills,
                        telemetry,
                        daemon,
                        new PrefixClusterCurator(),
                        executor,
                        CuratorIdleSignal.neverIdle(),
                        Duration.ofMinutes(5));

        UmbrellaConsolidationPlanner.PlanResult result = planner.runOnce().block();
        assertThat(result).isNotNull();
        assertThat(result.consolidation().applied()).isEmpty();
        assertThat(result.consolidation().skipped()).isEmpty();
    }
}
