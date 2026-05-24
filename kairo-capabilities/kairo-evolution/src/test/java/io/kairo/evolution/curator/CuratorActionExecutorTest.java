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
import io.kairo.api.evolution.SkillLifecycleState;
import io.kairo.api.evolution.SkillProvenance;
import io.kairo.api.evolution.SkillTelemetry;
import io.kairo.api.evolution.SkillTrustLevel;
import io.kairo.evolution.FileEvolvedSkillStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CuratorActionExecutorTest {

    private static EvolvedSkill skill(String name, String body) {
        Instant now = Instant.now();
        return new EvolvedSkill(
                name,
                "1.0",
                "desc " + name,
                body,
                "general",
                Set.of(),
                SkillTrustLevel.DRAFT,
                null,
                now,
                now,
                0L);
    }

    @Test
    void mergeFoldsSiblingsIntoUmbrellaAndArchivesThem(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();

        skills.save(skill("pr-triage", "# Umbrella\n\nbase body")).block();
        skills.save(skill("pr-fix-7", "# Fix 7\n\nfixes a bug")).block();
        skills.save(skill("pr-fix-8", "# Fix 8\n\nfixes another bug")).block();

        telemetry.recordUse("pr-triage", Instant.now()).block();
        telemetry.recordUse("pr-fix-7", Instant.now()).block();
        telemetry.recordUse("pr-fix-8", Instant.now()).block();

        CuratorActionExecutor exec = new CuratorActionExecutor(skills, telemetry);
        ConsolidationReport report =
                exec.apply(
                        List.of(
                                new CuratorAction.MergeIntoUmbrella(
                                        "pr-triage",
                                        List.of("pr-fix-7", "pr-fix-8"),
                                        "test merge")));

        assertThat(report.applied()).hasSize(1);
        EvolvedSkill umbrella = skills.get("pr-triage").block().orElseThrow();
        assertThat(umbrella.instructions()).contains("Absorbed from `pr-fix-7`");
        assertThat(umbrella.instructions()).contains("Absorbed from `pr-fix-8`");
        assertThat(skills.get("pr-fix-7").block()).isEmpty();
        assertThat(skills.get("pr-fix-8").block()).isEmpty();

        SkillTelemetry t7 = telemetry.get("pr-fix-7").block().orElseThrow();
        assertThat(t7.state()).isEqualTo(SkillLifecycleState.ARCHIVED);
        assertThat(t7.absorbedInto()).isEqualTo("pr-triage");
    }

    @Test
    void mergeSkipsImmuneSiblings(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();
        skills.save(skill("pr-triage", "# Umbrella")).block();
        skills.save(skill("pr-pinned", "# Pinned")).block();
        telemetry.recordUse("pr-triage", Instant.now()).block();
        telemetry.recordUse("pr-pinned", Instant.now()).block();
        telemetry.setPinned("pr-pinned", true, Instant.now()).block();

        CuratorActionExecutor exec = new CuratorActionExecutor(skills, telemetry);
        ConsolidationReport report =
                exec.apply(
                        List.of(
                                new CuratorAction.MergeIntoUmbrella(
                                        "pr-triage", List.of("pr-pinned"), "noop expected")));

        // Pinned sibling was skipped, but the merge itself is still recorded as applied
        // with 0 archived siblings — that's an honest accounting.
        assertThat(skills.get("pr-pinned").block()).isPresent();
        SkillTelemetry pinned = telemetry.get("pr-pinned").block().orElseThrow();
        assertThat(pinned.state()).isNotEqualTo(SkillLifecycleState.ARCHIVED);
        assertThat(report.applied()).isNotEmpty();
    }

    @Test
    void createUmbrellaWritesNewSkillAndArchivesSiblings(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();
        skills.save(skill("release-rc1", "rc1")).block();
        skills.save(skill("release-rc2", "rc2")).block();
        telemetry.recordUse("release-rc1", Instant.now()).block();
        telemetry.recordUse("release-rc2", Instant.now()).block();

        CuratorActionExecutor exec = new CuratorActionExecutor(skills, telemetry);
        ConsolidationReport report =
                exec.apply(
                        List.of(
                                new CuratorAction.CreateUmbrella(
                                        "release-prep",
                                        List.of("release-rc1", "release-rc2"),
                                        "# Release prep\n\nworkflow",
                                        "Release prep umbrella",
                                        "no broad umbrella exists")));

        assertThat(report.applied()).hasSize(1);
        assertThat(skills.get("release-prep").block()).isPresent();
        assertThat(skills.get("release-rc1").block()).isEmpty();
        assertThat(skills.get("release-rc2").block()).isEmpty();

        SkillTelemetry newUmbrella = telemetry.get("release-prep").block().orElseThrow();
        assertThat(newUmbrella.provenance()).isEqualTo(SkillProvenance.AGENT_CREATED);

        SkillTelemetry archived = telemetry.get("release-rc1").block().orElseThrow();
        assertThat(archived.absorbedInto()).isEqualTo("release-prep");
    }

    @Test
    void demoteWritesSupportFileAndArchivesSibling(@TempDir Path dir) throws Exception {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();
        Path supportDir = dir.resolve("support");
        Files.createDirectories(supportDir);

        skills.save(skill("deploys", "umbrella body")).block();
        skills.save(skill("gcp-quirks", "narrow content")).block();
        telemetry.recordUse("deploys", Instant.now()).block();
        telemetry.recordUse("gcp-quirks", Instant.now()).block();

        CuratorActionExecutor exec = new CuratorActionExecutor(skills, telemetry, supportDir);
        ConsolidationReport report =
                exec.apply(
                        List.of(
                                new CuratorAction.DemoteToSupport(
                                        "deploys",
                                        "gcp-quirks",
                                        CuratorAction.SupportKind.REFERENCES,
                                        "gcp-quirks.md",
                                        "# GCP quirks\n\nstuff",
                                        "narrow but valuable")));

        assertThat(report.applied()).hasSize(1);
        Path expectedFile = supportDir.resolve("deploys/references/gcp-quirks.md");
        assertThat(Files.exists(expectedFile)).isTrue();
        assertThat(Files.readString(expectedFile)).contains("# GCP quirks");
        assertThat(skills.get("gcp-quirks").block()).isEmpty();
    }

    @Test
    void archiveLeavesNoSkillButRecordsTelemetry(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();
        skills.save(skill("stale-session-23", "obsolete content")).block();
        telemetry.recordUse("stale-session-23", Instant.now()).block();

        CuratorActionExecutor exec = new CuratorActionExecutor(skills, telemetry);
        ConsolidationReport report =
                exec.apply(List.of(new CuratorAction.Archive("stale-session-23", "irrelevant")));

        assertThat(report.applied()).hasSize(1);
        assertThat(skills.get("stale-session-23").block()).isEmpty();
        SkillTelemetry t = telemetry.get("stale-session-23").block().orElseThrow();
        assertThat(t.state()).isEqualTo(SkillLifecycleState.ARCHIVED);
    }

    @Test
    void dryRunMakesNoMutations(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();
        skills.save(skill("pr-triage", "# Umbrella")).block();
        skills.save(skill("pr-fix", "# Fix")).block();
        telemetry.recordUse("pr-triage", Instant.now()).block();
        telemetry.recordUse("pr-fix", Instant.now()).block();

        CuratorActionExecutor exec = new CuratorActionExecutor(skills, telemetry);
        ConsolidationReport report =
                exec.apply(
                        List.of(
                                new CuratorAction.MergeIntoUmbrella(
                                        "pr-triage", List.of("pr-fix"), "test dry-run")),
                        true);

        assertThat(report.dryRun()).isTrue();
        // Neither store was mutated.
        assertThat(skills.get("pr-fix").block()).isPresent();
        SkillTelemetry t = telemetry.get("pr-fix").block().orElseThrow();
        assertThat(t.state()).isNotEqualTo(SkillLifecycleState.ARCHIVED);
    }

    @Test
    void missingUmbrellaIsSkipped(@TempDir Path dir) {
        FileEvolvedSkillStore skills = new FileEvolvedSkillStore(dir.resolve("skills"));
        InMemorySkillTelemetryStore telemetry = new InMemorySkillTelemetryStore();

        CuratorActionExecutor exec = new CuratorActionExecutor(skills, telemetry);
        ConsolidationReport report =
                exec.apply(List.of(new CuratorAction.Archive("ghost", "won't find this")));

        assertThat(report.applied()).isEmpty();
        assertThat(report.skipped()).hasSize(1);
        assertThat(report.skipped().get(0).message()).contains("missing");
    }
}
