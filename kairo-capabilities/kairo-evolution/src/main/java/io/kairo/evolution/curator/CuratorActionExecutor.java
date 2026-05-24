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

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTelemetry;
import io.kairo.api.evolution.SkillTelemetryStore;
import io.kairo.api.evolution.SkillTrustLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a list of {@link CuratorAction}s to the {@link EvolvedSkillStore} and {@link
 * SkillTelemetryStore} atomically per action, returning a {@link ConsolidationReport} that mirrors
 * Hermes' curator summary YAML (skills changed + reason for each).
 *
 * <p>{@code supportDirectory} is the local filesystem root where {@link
 * CuratorAction.DemoteToSupport} actions write their {@code references/}, {@code templates/}, and
 * {@code scripts/} files. When {@code null}, demote actions are recorded as "would write" but no
 * disk write happens — useful when the host store isn't file-backed.
 *
 * <p>Hard-rule enforcement (mirrors Hermes' invariants):
 *
 * <ul>
 *   <li>Pinned / non-agent-created skills are never archived or touched.
 *   <li>Sibling lookups that fail to resolve a real catalog member are skipped + logged.
 *   <li>Every archive records {@code absorbedInto} so downstream cron-job rewiring can migrate.
 * </ul>
 */
public final class CuratorActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(CuratorActionExecutor.class);

    private final EvolvedSkillStore skillStore;
    private final SkillTelemetryStore telemetryStore;
    private final Path supportDirectory;

    public CuratorActionExecutor(EvolvedSkillStore skillStore, SkillTelemetryStore telemetryStore) {
        this(skillStore, telemetryStore, null);
    }

    public CuratorActionExecutor(
            EvolvedSkillStore skillStore,
            SkillTelemetryStore telemetryStore,
            Path supportDirectory) {
        this.skillStore = skillStore;
        this.telemetryStore = telemetryStore;
        this.supportDirectory = supportDirectory;
    }

    /**
     * Apply actions in order. Returns a report listing what was actually applied (after skipping
     * actions that would touch immune skills or reference missing names).
     */
    public ConsolidationReport apply(List<CuratorAction> actions) {
        return apply(actions, false);
    }

    /**
     * @param dryRun if true, the report is produced but no store mutations or disk writes happen.
     */
    public ConsolidationReport apply(List<CuratorAction> actions, boolean dryRun) {
        Instant now = Instant.now();
        List<ConsolidationReport.Step> applied = new ArrayList<>();
        List<ConsolidationReport.Step> skipped = new ArrayList<>();

        for (CuratorAction action : actions) {
            try {
                ConsolidationReport.Step step = applyOne(action, now, dryRun);
                if (step.applied()) {
                    applied.add(step);
                } else {
                    skipped.add(step);
                }
            } catch (Exception e) {
                log.warn("Curator action failed: {} → {}", action, e.toString());
                skipped.add(
                        new ConsolidationReport.Step(
                                action, false, false, "exception: " + e.getMessage()));
            }
        }
        return new ConsolidationReport(now, dryRun, applied, skipped);
    }

    private ConsolidationReport.Step applyOne(CuratorAction action, Instant now, boolean dryRun) {
        return switch (action) {
            case CuratorAction.MergeIntoUmbrella merge -> applyMerge(merge, now, dryRun);
            case CuratorAction.CreateUmbrella create -> applyCreate(create, now, dryRun);
            case CuratorAction.DemoteToSupport demote -> applyDemote(demote, now, dryRun);
            case CuratorAction.Keep keep -> new ConsolidationReport.Step(keep, false, true, "keep");
            case CuratorAction.Archive archive -> applyArchive(archive, now, dryRun);
        };
    }

    private ConsolidationReport.Step applyMerge(
            CuratorAction.MergeIntoUmbrella merge, Instant now, boolean dryRun) {
        Optional<EvolvedSkill> umbrella = skillStore.get(merge.umbrella()).block();
        if (umbrella.isEmpty()) {
            return skipMissing(merge, "umbrella not found: " + merge.umbrella());
        }
        if (isImmune(merge.umbrella())) {
            return skipImmune(merge, merge.umbrella());
        }
        List<String> archivedSiblings = new ArrayList<>();
        StringBuilder mergedBody = new StringBuilder(umbrella.get().instructions());
        for (String sibling : merge.siblings()) {
            Optional<EvolvedSkill> siblingSkill = skillStore.get(sibling).block();
            if (siblingSkill.isEmpty()) {
                log.debug("Skipping merge — sibling missing: {}", sibling);
                continue;
            }
            if (isImmune(sibling)) {
                log.debug("Skipping merge — sibling immune: {}", sibling);
                continue;
            }
            mergedBody
                    .append("\n\n## Absorbed from `")
                    .append(sibling)
                    .append("`\n\n")
                    .append(siblingSkill.get().instructions().trim());
            archivedSiblings.add(sibling);
        }
        if (!dryRun) {
            skillStore.save(umbrella.get().withUpdatedInstructions(mergedBody.toString())).block();
            telemetryStore.recordPatch(merge.umbrella(), now).block();
            for (String s : archivedSiblings) {
                telemetryStore.archive(s, merge.umbrella(), now).block();
                skillStore.delete(s).block();
            }
        }
        return new ConsolidationReport.Step(
                merge,
                !dryRun || !archivedSiblings.isEmpty(),
                false,
                "merged " + archivedSiblings.size() + " sibling(s) into " + merge.umbrella());
    }

    private ConsolidationReport.Step applyCreate(
            CuratorAction.CreateUmbrella create, Instant now, boolean dryRun) {
        Optional<EvolvedSkill> existing = skillStore.get(create.umbrella()).block();
        if (existing.isPresent()) {
            return skipMissing(create, "umbrella already exists: " + create.umbrella());
        }
        EvolvedSkill umbrella =
                new EvolvedSkill(
                        create.umbrella(),
                        "1.0",
                        create.description() == null ? "" : create.description(),
                        create.skillBody() == null ? "" : create.skillBody(),
                        "umbrella",
                        Set.of("umbrella", "curator-generated"),
                        SkillTrustLevel.DRAFT,
                        null,
                        now,
                        now,
                        0L);
        List<String> archivedSiblings = new ArrayList<>();
        if (!dryRun) {
            skillStore.save(umbrella).block();
            telemetryStore
                    .save(
                            SkillTelemetry.initial(
                                    create.umbrella(),
                                    io.kairo.api.evolution.SkillProvenance.AGENT_CREATED,
                                    now))
                    .block();
            for (String sibling : create.siblings()) {
                if (isImmune(sibling)) continue;
                if (skillStore.get(sibling).block().isEmpty()) continue;
                telemetryStore.archive(sibling, create.umbrella(), now).block();
                skillStore.delete(sibling).block();
                archivedSiblings.add(sibling);
            }
        }
        return new ConsolidationReport.Step(
                create,
                true,
                false,
                "created " + create.umbrella() + " (absorbed " + archivedSiblings.size() + ")");
    }

    private ConsolidationReport.Step applyDemote(
            CuratorAction.DemoteToSupport demote, Instant now, boolean dryRun) {
        if (isImmune(demote.umbrella()) || isImmune(demote.sibling())) {
            return skipImmune(
                    demote, isImmune(demote.umbrella()) ? demote.umbrella() : demote.sibling());
        }
        if (skillStore.get(demote.umbrella()).block().isEmpty()) {
            return skipMissing(demote, "umbrella missing: " + demote.umbrella());
        }
        if (skillStore.get(demote.sibling()).block().isEmpty()) {
            return skipMissing(demote, "sibling missing: " + demote.sibling());
        }
        if (!dryRun) {
            if (supportDirectory != null) {
                String subdir =
                        switch (demote.supportKind()) {
                            case REFERENCES -> "references";
                            case TEMPLATES -> "templates";
                            case SCRIPTS -> "scripts";
                        };
                Path target =
                        supportDirectory
                                .resolve(demote.umbrella())
                                .resolve(subdir)
                                .resolve(
                                        demote.fileName() == null
                                                ? demote.sibling() + ".md"
                                                : demote.fileName());
                try {
                    Files.createDirectories(target.getParent());
                    Files.writeString(
                            target,
                            demote.body() == null ? "" : demote.body(),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                } catch (Exception e) {
                    log.warn("Failed to write demote file {}: {}", target, e.toString());
                }
            }
            telemetryStore.archive(demote.sibling(), demote.umbrella(), now).block();
            skillStore.delete(demote.sibling()).block();
        }
        return new ConsolidationReport.Step(
                demote,
                true,
                false,
                "demoted "
                        + demote.sibling()
                        + " → "
                        + demote.umbrella()
                        + "/"
                        + demote.supportKind());
    }

    private ConsolidationReport.Step applyArchive(
            CuratorAction.Archive archive, Instant now, boolean dryRun) {
        if (isImmune(archive.umbrella())) {
            return skipImmune(archive, archive.umbrella());
        }
        if (skillStore.get(archive.umbrella()).block().isEmpty()) {
            return skipMissing(archive, "skill missing: " + archive.umbrella());
        }
        if (!dryRun) {
            telemetryStore.archive(archive.umbrella(), null, now).block();
            skillStore.delete(archive.umbrella()).block();
        }
        return new ConsolidationReport.Step(archive, true, false, "archived");
    }

    private boolean isImmune(String name) {
        Optional<SkillTelemetry> t = telemetryStore.get(name).block();
        return t.map(SkillTelemetry::isCuratorImmune).orElse(false);
    }

    private static ConsolidationReport.Step skipImmune(CuratorAction a, String name) {
        return new ConsolidationReport.Step(a, false, false, "skipped: " + name + " is immune");
    }

    private static ConsolidationReport.Step skipMissing(CuratorAction a, String reason) {
        return new ConsolidationReport.Step(a, false, false, "skipped: " + reason);
    }
}
