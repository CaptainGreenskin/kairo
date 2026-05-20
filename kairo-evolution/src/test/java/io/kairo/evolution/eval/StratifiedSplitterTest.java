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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StratifiedSplitterTest {

    private static List<SkillEvalQuery> queries(int pos, int neg) {
        List<SkillEvalQuery> out = new ArrayList<>();
        for (int i = 0; i < pos; i++) out.add(new SkillEvalQuery("pos-" + i, true));
        for (int i = 0; i < neg; i++) out.add(new SkillEvalQuery("neg-" + i, false));
        return out;
    }

    @Test
    void splitsAtRoughlyTheGivenHoldout() {
        StratifiedSplitter splitter = new StratifiedSplitter(0.4);
        StratifiedSplitter.Split split = splitter.split(queries(10, 10));

        // 0.4 of 10 = 4 in each class.
        assertThat(split.test()).hasSize(8);
        assertThat(split.train()).hasSize(12);

        long posInTest = split.test().stream().filter(SkillEvalQuery::shouldTrigger).count();
        long negInTest = split.test().stream().filter(q -> !q.shouldTrigger()).count();
        assertThat(posInTest).isEqualTo(4);
        assertThat(negInTest).isEqualTo(4);
    }

    @Test
    void zeroHoldoutKeepsAllInTrain() {
        StratifiedSplitter.Split split = new StratifiedSplitter(0.0).split(queries(5, 5));
        assertThat(split.train()).hasSize(10);
        assertThat(split.test()).isEmpty();
    }

    @Test
    void seededSplitIsDeterministic() {
        StratifiedSplitter s1 = new StratifiedSplitter(0.3, 99L);
        StratifiedSplitter s2 = new StratifiedSplitter(0.3, 99L);
        List<SkillEvalQuery> all = queries(10, 10);
        assertThat(s1.split(all)).isEqualTo(s2.split(all));
    }

    @Test
    void tinySetStillProducesAtLeastOneOfEachInTest() {
        // 2 positives, 2 negatives, 0.4 holdout → max(1, 0.8) = 1 each in test.
        StratifiedSplitter.Split split = new StratifiedSplitter(0.4).split(queries(2, 2));
        long posTest = split.test().stream().filter(SkillEvalQuery::shouldTrigger).count();
        long negTest = split.test().stream().filter(q -> !q.shouldTrigger()).count();
        assertThat(posTest).isEqualTo(1);
        assertThat(negTest).isEqualTo(1);
    }
}
