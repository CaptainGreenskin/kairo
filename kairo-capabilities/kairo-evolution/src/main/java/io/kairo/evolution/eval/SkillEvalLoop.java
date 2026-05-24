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
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the eval ↔ improve loop equivalent to deer-flow's {@code run_loop.py}:
 *
 * <ol>
 *   <li>Split the eval set into train and test (stratified, seeded).
 *   <li>For each iteration up to {@code maxIterations}:
 *       <ul>
 *         <li>Run the current description over train + test with {@link SkillEvalRunner}.
 *         <li>Record history.
 *         <li>If train pass-rate == 1.0, stop early ("all-pass").
 *         <li>Otherwise, call {@link LlmDescriptionOptimizer} to propose a new description and
 *             repeat. If the new description is identical to the current one, stop ("converged").
 *       </ul>
 *   <li>Return the {@link Report} including the {@code bestDescription} selected by TEST pass-rate
 *       (ties broken by train pass-rate, then earlier iteration), per deer-flow's "avoid
 *       overfitting" heuristic.
 * </ol>
 */
public final class SkillEvalLoop {

    private static final Logger log = LoggerFactory.getLogger(SkillEvalLoop.class);

    private final SkillEvalRunner runner;
    private final LlmDescriptionOptimizer optimizer;
    private final StratifiedSplitter splitter;
    private final int maxIterations;

    public SkillEvalLoop(
            SkillEvalRunner runner,
            LlmDescriptionOptimizer optimizer,
            StratifiedSplitter splitter,
            int maxIterations) {
        if (maxIterations <= 0) throw new IllegalArgumentException("maxIterations must be > 0");
        this.runner = runner;
        this.optimizer = optimizer;
        this.splitter = splitter;
        this.maxIterations = maxIterations;
    }

    public Mono<Report> run(
            String skillName,
            String skillBody,
            String initialDescription,
            List<SkillEvalQuery> evalSet) {
        StratifiedSplitter.Split split = splitter.split(evalSet);
        List<Report.Iteration> history = new ArrayList<>();
        return iterate(skillName, skillBody, initialDescription, split, history, 1)
                .map(exitReason -> finish(history, exitReason));
    }

    private Mono<String> iterate(
            String skillName,
            String skillBody,
            String currentDescription,
            StratifiedSplitter.Split split,
            List<Report.Iteration> history,
            int iter) {
        return runner.run(skillName, currentDescription, split.train())
                .flatMap(
                        trainResults ->
                                runner.run(skillName, currentDescription, split.test())
                                        .flatMap(
                                                testResults -> {
                                                    SkillEvalResult.Summary trainSummary =
                                                            SkillEvalResult.Summary.of(
                                                                    trainResults);
                                                    SkillEvalResult.Summary testSummary =
                                                            SkillEvalResult.Summary.of(testResults);
                                                    history.add(
                                                            new Report.Iteration(
                                                                    iter,
                                                                    currentDescription,
                                                                    trainResults,
                                                                    testResults,
                                                                    trainSummary,
                                                                    testSummary));
                                                    log.info(
                                                            "Eval iter {}: train={}/{} test={}/{}",
                                                            iter,
                                                            trainSummary.passed(),
                                                            trainSummary.total(),
                                                            testSummary.passed(),
                                                            testSummary.total());

                                                    if (trainSummary.passRate() >= 1.0) {
                                                        return Mono.just("all-pass");
                                                    }
                                                    if (iter >= maxIterations) {
                                                        return Mono.just("max-iterations");
                                                    }
                                                    return optimizer
                                                            .improve(
                                                                    buildContext(
                                                                            skillName,
                                                                            skillBody,
                                                                            currentDescription,
                                                                            trainResults,
                                                                            history))
                                                            .flatMap(
                                                                    next -> {
                                                                        if (next == null
                                                                                || next.equals(
                                                                                        currentDescription)) {
                                                                            return Mono.just(
                                                                                    "converged");
                                                                        }
                                                                        return iterate(
                                                                                skillName,
                                                                                skillBody, next,
                                                                                split, history,
                                                                                iter + 1);
                                                                    });
                                                }));
    }

    private DescriptionOptimizationContext buildContext(
            String skillName,
            String skillBody,
            String currentDescription,
            List<SkillEvalResult> trainResults,
            List<Report.Iteration> history) {
        List<SkillEvalResult> failedTriggers = new ArrayList<>();
        List<SkillEvalResult> falseTriggers = new ArrayList<>();
        for (SkillEvalResult r : trainResults) {
            if (r.pass()) continue;
            if (r.query().shouldTrigger()) {
                failedTriggers.add(r);
            } else {
                falseTriggers.add(r);
            }
        }
        List<DescriptionOptimizationContext.HistoryEntry> entries = new ArrayList<>(history.size());
        for (Report.Iteration it : history) {
            entries.add(
                    new DescriptionOptimizationContext.HistoryEntry(
                            it.iteration(), it.description(), it.trainSummary(), it.testSummary()));
        }
        return new DescriptionOptimizationContext(
                skillName, skillBody, currentDescription, failedTriggers, falseTriggers, entries);
    }

    private Report finish(List<Report.Iteration> history, String exitReason) {
        Report.Iteration best =
                history.stream()
                        .max(
                                Comparator.comparingDouble(
                                                (Report.Iteration it) ->
                                                        it.testSummary().passRate())
                                        .thenComparingDouble(it -> it.trainSummary().passRate())
                                        .thenComparing(
                                                Comparator.comparingInt(Report.Iteration::iteration)
                                                        .reversed()))
                        .orElseThrow();
        return new Report(history, best.description(), best, exitReason);
    }

    /** End-to-end result of the loop. */
    public record Report(
            List<Iteration> history,
            String bestDescription,
            Iteration bestIteration,
            String exitReason) {

        public Report {
            history = List.copyOf(history);
        }

        public record Iteration(
                int iteration,
                String description,
                List<SkillEvalResult> trainResults,
                List<SkillEvalResult> testResults,
                SkillEvalResult.Summary trainSummary,
                SkillEvalResult.Summary testSummary) {

            public Iteration {
                trainResults = List.copyOf(trainResults);
                testResults = List.copyOf(testResults);
            }
        }
    }
}
