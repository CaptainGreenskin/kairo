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

/**
 * Per-query outcome from {@link SkillEvalRunner}. Mirrors the shape deer-flow's run_eval.py emits.
 *
 * @param query the query being tested
 * @param triggers how many of the {@code runs} attempts actually triggered the skill
 * @param runs total attempts (typically {@code runsPerQuery=3})
 * @param triggerThreshold the fraction of attempts above which we consider the skill to have
 *     reliably triggered (e.g. 0.5 = "more than half the runs"). Used to compute {@link #pass()}.
 */
public record SkillEvalResult(
        SkillEvalQuery query, int triggers, int runs, double triggerThreshold) {

    public SkillEvalResult {
        if (runs <= 0) throw new IllegalArgumentException("runs must be > 0");
        if (triggers < 0 || triggers > runs) {
            throw new IllegalArgumentException("triggers must be in [0, runs]");
        }
        if (triggerThreshold < 0.0 || triggerThreshold > 1.0) {
            throw new IllegalArgumentException("triggerThreshold must be in [0, 1]");
        }
    }

    public double triggerRate() {
        return (double) triggers / runs;
    }

    /**
     * True if the skill's behavior matches the expected one: for {@code shouldTrigger=true}
     * queries, we expect triggerRate &gt; threshold; for {@code shouldTrigger=false}, we expect
     * triggerRate &lt;= threshold.
     */
    public boolean pass() {
        boolean reliable = triggerRate() > triggerThreshold;
        return reliable == query.shouldTrigger();
    }

    /** Summary statistics for a list of {@link SkillEvalResult}s. */
    public record Summary(int total, int passed, double passRate) {
        public static Summary of(List<SkillEvalResult> results) {
            int passed = (int) results.stream().filter(SkillEvalResult::pass).count();
            int total = results.size();
            return new Summary(total, passed, total == 0 ? 0.0 : (double) passed / total);
        }
    }
}
