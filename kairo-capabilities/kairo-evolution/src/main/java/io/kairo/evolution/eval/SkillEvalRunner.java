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
package io.kairo.evolution.eval;

import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs a list of {@link SkillEvalQuery}s through a {@link TriggerDecider} multiple times each and
 * aggregates per-query trigger rates. Mirrors deer-flow's {@code run_eval.py}.
 */
public final class SkillEvalRunner {

    private final TriggerDecider decider;
    private final int runsPerQuery;
    private final double triggerThreshold;
    private final int concurrency;

    public SkillEvalRunner(TriggerDecider decider) {
        this(decider, 3, 0.5, 4);
    }

    /**
     * @param decider how to ask the model whether a skill triggers
     * @param runsPerQuery how many times to repeat each query (deer-flow default: 3)
     * @param triggerThreshold fraction above which we consider the trigger reliable (default 0.5)
     * @param concurrency how many queries to evaluate in parallel
     */
    public SkillEvalRunner(
            TriggerDecider decider, int runsPerQuery, double triggerThreshold, int concurrency) {
        if (runsPerQuery <= 0) throw new IllegalArgumentException("runsPerQuery must be > 0");
        if (concurrency <= 0) throw new IllegalArgumentException("concurrency must be > 0");
        this.decider = decider;
        this.runsPerQuery = runsPerQuery;
        this.triggerThreshold = triggerThreshold;
        this.concurrency = concurrency;
    }

    public Mono<List<SkillEvalResult>> run(
            String skillName, String description, List<SkillEvalQuery> queries) {
        return Flux.fromIterable(queries)
                .flatMap(
                        query -> evaluateOne(skillName, description, query),
                        Math.max(1, concurrency))
                .collectList();
    }

    private Mono<SkillEvalResult> evaluateOne(
            String skillName, String description, SkillEvalQuery query) {
        return Flux.range(0, runsPerQuery)
                .flatMap(i -> decider.wouldTrigger(skillName, description, query.prompt()))
                .collectList()
                .map(
                        bools -> {
                            int triggers = 0;
                            for (Boolean b : bools) {
                                if (Boolean.TRUE.equals(b)) triggers++;
                            }
                            return new SkillEvalResult(
                                    query, triggers, runsPerQuery, triggerThreshold);
                        });
    }

    public int runsPerQuery() {
        return runsPerQuery;
    }

    public double triggerThreshold() {
        return triggerThreshold;
    }
}
