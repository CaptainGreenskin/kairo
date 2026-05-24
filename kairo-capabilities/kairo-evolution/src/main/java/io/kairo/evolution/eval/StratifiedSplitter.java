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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Splits eval queries into train / test sets, stratified by {@link SkillEvalQuery#shouldTrigger()},
 * with a fixed seed for reproducibility. Mirrors {@code split_eval_set(holdout, seed)} in
 * deer-flow's run_loop.py.
 */
public final class StratifiedSplitter {

    public record Split(List<SkillEvalQuery> train, List<SkillEvalQuery> test) {
        public Split {
            train = List.copyOf(train);
            test = List.copyOf(test);
        }
    }

    private final double holdout;
    private final long seed;

    public StratifiedSplitter(double holdout) {
        this(holdout, 42L);
    }

    public StratifiedSplitter(double holdout, long seed) {
        if (holdout < 0.0 || holdout >= 1.0) {
            throw new IllegalArgumentException("holdout must be in [0, 1)");
        }
        this.holdout = holdout;
        this.seed = seed;
    }

    public Split split(List<SkillEvalQuery> all) {
        if (holdout == 0.0) {
            return new Split(List.copyOf(all), List.of());
        }
        List<SkillEvalQuery> positive = new ArrayList<>();
        List<SkillEvalQuery> negative = new ArrayList<>();
        for (SkillEvalQuery q : all) {
            (q.shouldTrigger() ? positive : negative).add(q);
        }
        Random rng = new Random(seed);
        Collections.shuffle(positive, rng);
        Collections.shuffle(negative, rng);

        int nPosTest = Math.max(1, (int) (positive.size() * holdout));
        int nNegTest = Math.max(1, (int) (negative.size() * holdout));
        if (positive.isEmpty()) nPosTest = 0;
        if (negative.isEmpty()) nNegTest = 0;

        List<SkillEvalQuery> test = new ArrayList<>();
        test.addAll(positive.subList(0, Math.min(nPosTest, positive.size())));
        test.addAll(negative.subList(0, Math.min(nNegTest, negative.size())));

        List<SkillEvalQuery> train = new ArrayList<>();
        train.addAll(positive.subList(Math.min(nPosTest, positive.size()), positive.size()));
        train.addAll(negative.subList(Math.min(nNegTest, negative.size()), negative.size()));
        return new Split(train, test);
    }
}
