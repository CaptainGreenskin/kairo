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

import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SkillEvalRunnerTest {

    /** Deterministic decider: triggers iff prompt contains any word from the description. */
    private static TriggerDecider keywordDecider() {
        return (name, description, prompt) -> {
            for (String word : description.toLowerCase().split("[^a-z0-9]+")) {
                if (word.length() >= 4 && prompt.toLowerCase().contains(word)) {
                    return Mono.just(true);
                }
            }
            return Mono.just(false);
        };
    }

    @Test
    void positiveQueryWithMatchingKeywordPasses() {
        SkillEvalRunner runner = new SkillEvalRunner(keywordDecider(), 3, 0.5, 2);
        List<SkillEvalResult> results =
                runner.run(
                                "pdf-extract",
                                "Extract text from PDF files",
                                List.of(new SkillEvalQuery("extract pdf for me please", true)))
                        .block();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).triggerRate()).isEqualTo(1.0);
        assertThat(results.get(0).pass()).isTrue();
    }

    @Test
    void negativeQueryThatDoesNotTriggerPasses() {
        SkillEvalRunner runner = new SkillEvalRunner(keywordDecider(), 3, 0.5, 2);
        List<SkillEvalResult> results =
                runner.run(
                                "pdf-extract",
                                "Extract text from PDF files",
                                List.of(new SkillEvalQuery("schedule a meeting", false)))
                        .block();

        assertThat(results.get(0).triggerRate()).isEqualTo(0.0);
        assertThat(results.get(0).pass()).isTrue();
    }

    @Test
    void falseTriggerIsRecorded() {
        SkillEvalRunner runner = new SkillEvalRunner(keywordDecider(), 3, 0.5, 2);
        List<SkillEvalResult> results =
                runner.run(
                                "pdf-extract",
                                "Extract text from PDF files",
                                List.of(new SkillEvalQuery("extract data from json", false)))
                        .block();

        assertThat(results.get(0).pass()).isFalse();
    }

    @Test
    void summaryComputesPassRate() {
        SkillEvalResult.Summary summary =
                SkillEvalResult.Summary.of(
                        List.of(
                                new SkillEvalResult(new SkillEvalQuery("a", true), 3, 3, 0.5),
                                new SkillEvalResult(new SkillEvalQuery("b", true), 0, 3, 0.5),
                                new SkillEvalResult(new SkillEvalQuery("c", false), 0, 3, 0.5)));
        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.passed()).isEqualTo(2);
        assertThat(summary.passRate()).isEqualTo(2.0 / 3.0);
    }
}
