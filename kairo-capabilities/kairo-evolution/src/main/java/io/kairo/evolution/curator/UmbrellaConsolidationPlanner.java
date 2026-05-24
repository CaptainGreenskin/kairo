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
import io.kairo.api.evolution.SkillLifecycleState;
import io.kairo.api.evolution.SkillTelemetry;
import io.kairo.api.evolution.SkillTelemetryStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * High-level orchestrator that ties the M1 lifecycle pass together with the M2 LLM-driven
 * consolidation pass.
 *
 * <ol>
 *   <li>Run the {@link LifecycleCuratorDaemon} to apply automatic lifecycle transitions.
 *   <li>Build a {@link SkillCatalog} of agent-created, non-archived, non-pinned skills.
 *   <li>Hand it to the {@link LlmSkillCurator}.
 *   <li>Apply the returned actions via {@link CuratorActionExecutor} (or just collect them, if
 *       dry-run).
 * </ol>
 *
 * <p>Use {@link #dryRun()} when you want to preview what the curator would do without mutating
 * state — mirrors Hermes' {@code hermes curator run --dry-run}.
 */
public final class UmbrellaConsolidationPlanner {

    private static final Logger log = LoggerFactory.getLogger(UmbrellaConsolidationPlanner.class);

    private final EvolvedSkillStore skillStore;
    private final SkillTelemetryStore telemetryStore;
    private final LifecycleCuratorDaemon lifecycleDaemon;
    private final LlmSkillCurator llmCurator;
    private final CuratorActionExecutor executor;
    private final CuratorIdleSignal idleSignal;
    private final Duration idleThreshold;

    public UmbrellaConsolidationPlanner(
            EvolvedSkillStore skillStore,
            SkillTelemetryStore telemetryStore,
            LifecycleCuratorDaemon lifecycleDaemon,
            LlmSkillCurator llmCurator,
            CuratorActionExecutor executor,
            CuratorIdleSignal idleSignal,
            Duration idleThreshold) {
        this.skillStore = skillStore;
        this.telemetryStore = telemetryStore;
        this.lifecycleDaemon = lifecycleDaemon;
        this.llmCurator = llmCurator;
        this.executor = executor;
        this.idleSignal = idleSignal;
        this.idleThreshold = idleThreshold;
    }

    /** Run a full pass and apply mutations. */
    public Mono<PlanResult> runOnce() {
        return run(false);
    }

    /**
     * Run a full pass that produces a report without mutating state. The lifecycle daemon is
     * skipped entirely (it has no dry-run mode) so callers get a pure preview of consolidation
     * decisions.
     */
    public Mono<PlanResult> dryRun() {
        return run(true);
    }

    private Mono<PlanResult> run(boolean dryRun) {
        if (!idleSignal.idleFor(idleThreshold)) {
            log.debug("Planner skipped — host not idle for {}", idleThreshold);
            return Mono.just(
                    new PlanResult(
                            dryRun,
                            null,
                            new ConsolidationReport(
                                    java.time.Instant.now(), dryRun, List.of(), List.of())));
        }

        LifecycleTransitionResult lifecycle = dryRun ? null : lifecycleDaemon.runOnce(true);

        return buildCatalog()
                .flatMap(llmCurator::propose)
                .map(actions -> executor.apply(actions, dryRun))
                .map(report -> new PlanResult(dryRun, lifecycle, report));
    }

    private Mono<SkillCatalog> buildCatalog() {
        return skillStore
                .list()
                .collectList()
                .flatMap(
                        skills ->
                                telemetryStore
                                        .list()
                                        .collectList()
                                        .map(telemetry -> buildEntries(skills, telemetry)));
    }

    private SkillCatalog buildEntries(List<EvolvedSkill> skills, List<SkillTelemetry> telemetry) {
        Map<String, SkillTelemetry> byName = new HashMap<>(telemetry.size());
        for (SkillTelemetry t : telemetry) {
            byName.put(t.skillName(), t);
        }
        List<SkillCatalog.Entry> candidates = new ArrayList<>();
        List<SkillCatalog.Entry> immune = new ArrayList<>();
        for (EvolvedSkill s : skills) {
            SkillTelemetry t = byName.get(s.name());
            if (t != null && t.state() == SkillLifecycleState.ARCHIVED) {
                continue;
            }
            if (t != null && t.isCuratorImmune()) {
                immune.add(new SkillCatalog.Entry(s, t));
            } else {
                candidates.add(new SkillCatalog.Entry(s, t));
            }
        }
        return new SkillCatalog(candidates, immune);
    }

    /** Combined result: optional lifecycle pass + consolidation report. */
    public record PlanResult(
            boolean dryRun,
            LifecycleTransitionResult lifecycle,
            ConsolidationReport consolidation) {

        public int totalChanged() {
            int l = lifecycle == null ? 0 : lifecycle.totalChanged();
            return l + consolidation.applied().size();
        }
    }
}
