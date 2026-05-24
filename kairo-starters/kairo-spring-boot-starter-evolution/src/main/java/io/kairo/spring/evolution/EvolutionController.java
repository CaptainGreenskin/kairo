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

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTelemetry;
import io.kairo.api.evolution.SkillTelemetryStore;
import io.kairo.evolution.curator.ConsolidationReport;
import io.kairo.evolution.curator.LifecycleCuratorDaemon;
import io.kairo.evolution.curator.LifecycleTransitionResult;
import io.kairo.evolution.curator.UmbrellaConsolidationPlanner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Hand-wired REST controller for the curator-managed skill library. Lives in the starter so it can
 * stay optional — applications that don't include Spring Web simply won't see it (this class isn't
 * a {@code @RestController}; the host registers an instance via {@link #handle...} methods).
 *
 * <p>Path conventions match Hermes' {@code hermes curator} CLI flags so the React UI in M5 reuses
 * the same vocabulary:
 *
 * <ul>
 *   <li>{@code GET /evolution/skills} — list skills with telemetry
 *   <li>{@code POST /evolution/skills/{name}/pin} — pin a skill
 *   <li>{@code POST /evolution/skills/{name}/unpin} — unpin a skill
 *   <li>{@code POST /evolution/skills/{name}/archive} — manual archive
 *   <li>{@code POST /evolution/curator/run?dry=true|false} — run a curator pass
 *   <li>{@code POST /evolution/curator/lifecycle/run} — run only the lifecycle pass
 * </ul>
 */
public final class EvolutionController {

    private final EvolvedSkillStore skills;
    private final SkillTelemetryStore telemetry;
    private final LifecycleCuratorDaemon daemon;
    private final UmbrellaConsolidationPlanner planner;

    public EvolutionController(
            EvolvedSkillStore skills,
            SkillTelemetryStore telemetry,
            LifecycleCuratorDaemon daemon,
            UmbrellaConsolidationPlanner planner) {
        this.skills = skills;
        this.telemetry = telemetry;
        this.daemon = daemon;
        this.planner = planner;
    }

    /** Aggregate skill catalog + telemetry into a single response payload. */
    public Mono<List<SkillView>> listSkills() {
        return skills.list()
                .collectList()
                .flatMap(
                        skillList ->
                                telemetry
                                        .list()
                                        .collectList()
                                        .map(
                                                telList -> {
                                                    Map<String, SkillTelemetry> byName =
                                                            new HashMap<>(telList.size());
                                                    for (SkillTelemetry t : telList) {
                                                        byName.put(t.skillName(), t);
                                                    }
                                                    List<SkillView> out = new ArrayList<>();
                                                    for (EvolvedSkill s : skillList) {
                                                        out.add(
                                                                new SkillView(
                                                                        s, byName.get(s.name())));
                                                    }
                                                    return out;
                                                }));
    }

    public Mono<SkillTelemetry> pin(String name) {
        return telemetry.setPinned(name, true, Instant.now());
    }

    public Mono<SkillTelemetry> unpin(String name) {
        return telemetry.setPinned(name, false, Instant.now());
    }

    /** Manual archive — used by operators to remove a skill without waiting for the curator. */
    public Mono<SkillTelemetry> archive(String name) {
        return telemetry
                .archive(name, null, Instant.now())
                .flatMap(t -> skills.delete(name).thenReturn(t));
    }

    /**
     * Run a full curator pass — lifecycle transitions + LLM consolidation. Honors {@code dry} for
     * the consolidation half; the lifecycle half is read-only when {@code dry=true}.
     */
    public Mono<UmbrellaConsolidationPlanner.PlanResult> runCurator(boolean dry) {
        return dry ? planner.dryRun() : planner.runOnce();
    }

    /**
     * Force the lifecycle pass (no consolidation). Used when the operator just wants to age data.
     */
    public Mono<LifecycleTransitionResult> runLifecycle() {
        return Mono.fromSupplier(() -> daemon.runOnce(true));
    }

    /** Carrier returned by {@link #listSkills()}. */
    public record SkillView(EvolvedSkill skill, SkillTelemetry telemetry) {}

    /** Convenience: same as {@link UmbrellaConsolidationPlanner.PlanResult} but flattened. */
    public record CuratorRunResponse(
            boolean dryRun,
            LifecycleTransitionResult lifecycle,
            ConsolidationReport consolidation,
            int totalChanged) {
        public static CuratorRunResponse from(UmbrellaConsolidationPlanner.PlanResult r) {
            return new CuratorRunResponse(
                    r.dryRun(), r.lifecycle(), r.consolidation(), r.totalChanged());
        }
    }
}
