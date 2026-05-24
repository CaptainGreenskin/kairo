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

class SkillEvalLoopTest {

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
    void loopReturnsBestByTestScoreNotTrainScore() {
        // Engineered scenario:
        //   iter 1 description: keyword "schedule" — triggers neg query "schedule meeting" (false
        // trigger)
        //   iter 2 description: keyword "pdfextraction" — triggers positive query "extract pdf"
        //                       and never triggers the negative one. (Both train+test improve.)
        SkillEvalRunner runner = new SkillEvalRunner(keywordDecider(), 3, 0.5, 2);

        // Optimizer returns iter-2 description on first call, then converges.
        LlmDescriptionOptimizer optimizer =
                ctx -> {
                    if (ctx.history().size() == 1) {
                        return Mono.just("Extract pdfextraction from documents");
                    }
                    return Mono.just(ctx.currentDescription());
                };

        StratifiedSplitter splitter = new StratifiedSplitter(0.5, 7L);

        SkillEvalLoop loop = new SkillEvalLoop(runner, optimizer, splitter, 3);

        List<SkillEvalQuery> evals =
                List.of(
                        new SkillEvalQuery("please extract a pdf for me", true),
                        new SkillEvalQuery("convert this pdf to text", true),
                        new SkillEvalQuery("schedule a meeting tomorrow", false),
                        new SkillEvalQuery("set up a calendar entry", false));

        SkillEvalLoop.Report report =
                loop.run("pdf-extract", "# PDF skill body", "Schedule tasks", evals).block();

        assertThat(report).isNotNull();
        assertThat(report.history().size()).isGreaterThanOrEqualTo(1);
        assertThat(report.bestDescription())
                .as("best should be the improved description, not the original")
                .isEqualTo("Extract pdfextraction from documents");
    }

    @Test
    void stopsEarlyWhenTrainAllPasses() {
        SkillEvalRunner runner = new SkillEvalRunner(keywordDecider(), 3, 0.5, 2);
        LlmDescriptionOptimizer never =
                ctx -> {
                    throw new IllegalStateException("optimizer should not be called");
                };
        StratifiedSplitter splitter = new StratifiedSplitter(0.0, 7L);
        SkillEvalLoop loop = new SkillEvalLoop(runner, never, splitter, 5);

        List<SkillEvalQuery> evals =
                List.of(
                        new SkillEvalQuery("extract pdf now", true),
                        new SkillEvalQuery("set up a calendar entry", false));

        SkillEvalLoop.Report report =
                loop.run("pdf-extract", "body", "Extract pdf from documents", evals).block();

        assertThat(report.exitReason()).isEqualTo("all-pass");
        assertThat(report.history()).hasSize(1);
    }

    @Test
    void stopsOnConvergenceWhenOptimizerReturnsSameDescription() {
        SkillEvalRunner runner = new SkillEvalRunner(keywordDecider(), 3, 0.5, 2);
        // Optimizer just returns the same description → loop should detect convergence.
        LlmDescriptionOptimizer stuck = ctx -> Mono.just(ctx.currentDescription());
        StratifiedSplitter splitter = new StratifiedSplitter(0.5, 7L);
        SkillEvalLoop loop = new SkillEvalLoop(runner, stuck, splitter, 5);

        List<SkillEvalQuery> evals =
                List.of(
                        new SkillEvalQuery("query that fails", true),
                        new SkillEvalQuery("query that fails too", true),
                        new SkillEvalQuery("totally unrelated", false),
                        new SkillEvalQuery("also unrelated", false));

        SkillEvalLoop.Report report = loop.run("foo", "body", "noooop description", evals).block();

        assertThat(report.exitReason()).isEqualTo("converged");
        // history should be exactly 1 because we converge right after the first iteration.
        assertThat(report.history()).hasSize(1);
    }

    @Test
    void historyEntryRecordsBothTrainAndTestPassRates() {
        SkillEvalRunner runner = new SkillEvalRunner(keywordDecider(), 3, 0.5, 2);
        LlmDescriptionOptimizer stuck = ctx -> Mono.just(ctx.currentDescription());
        StratifiedSplitter splitter = new StratifiedSplitter(0.5, 7L);
        SkillEvalLoop loop = new SkillEvalLoop(runner, stuck, splitter, 2);

        // Need enough queries so a 0.5 holdout leaves something in train AND test.
        List<SkillEvalQuery> evals =
                List.of(
                        new SkillEvalQuery("extract a pdf for me", true),
                        new SkillEvalQuery("convert this pdf to text", true),
                        new SkillEvalQuery("schedule meeting", false),
                        new SkillEvalQuery("book conference room", false));

        SkillEvalLoop.Report report = loop.run("pdf-extract", "body", "Extract pdf", evals).block();
        SkillEvalLoop.Report.Iteration iter1 = report.history().get(0);
        assertThat(iter1.trainSummary().total()).isGreaterThan(0);
        assertThat(iter1.testSummary().total()).isGreaterThan(0);
    }
}
