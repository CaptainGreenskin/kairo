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
package io.kairo.evolution;

import io.kairo.api.evolution.EvolutionContext;
import io.kairo.api.evolution.EvolutionCounters;
import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Runs N rounds of benchmark challenges, triggering evolution between rounds, and measures
 * improvement across rounds.
 *
 * <p>Each round executes a set of challenges via a user-provided runner function, collects
 * pass/fail results, then submits failures to the evolution pipeline. The next round benefits from
 * any skills evolved in previous rounds.
 *
 * <p>The benchmark produces a {@link BenchmarkReport} with per-round success rates and the overall
 * improvement curve.
 */
public final class EvolutionBenchmarkRunner {

    private static final Logger log = LoggerFactory.getLogger(EvolutionBenchmarkRunner.class);

    private final EvolutionPipelineOrchestrator orchestrator;
    private final EvolvedSkillStore skillStore;
    private final int rounds;
    private final String agentName;

    /**
     * @param orchestrator the evolution pipeline for processing failures
     * @param skillStore the skill store (evolved skills are visible to subsequent rounds)
     * @param rounds number of benchmark rounds (typically 5)
     * @param agentName agent identifier for evolution context
     */
    public EvolutionBenchmarkRunner(
            EvolutionPipelineOrchestrator orchestrator,
            EvolvedSkillStore skillStore,
            int rounds,
            String agentName) {
        if (rounds < 1) throw new IllegalArgumentException("rounds must be >= 1");
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.skillStore = Objects.requireNonNull(skillStore);
        this.rounds = rounds;
        this.agentName = Objects.requireNonNull(agentName);
    }

    /**
     * Run the benchmark. The challengeRunner executes all challenges for one round and returns
     * results.
     *
     * @param challenges the set of challenges to run each round
     * @param challengeRunner function that takes a challenge and returns a Mono of ChallengeResult
     * @return the benchmark report
     */
    public <C> Mono<BenchmarkReport> run(
            List<C> challenges, Function<C, Mono<ChallengeResult>> challengeRunner) {

        return Mono.defer(
                () -> {
                    List<RoundResult> roundResults = new ArrayList<>();

                    Mono<Void> chain = Mono.empty();
                    for (int round = 1; round <= rounds; round++) {
                        final int roundNum = round;
                        chain =
                                chain.then(
                                        runRound(challenges, challengeRunner, roundNum)
                                                .doOnNext(roundResults::add)
                                                .then());
                    }

                    return chain.then(
                            Mono.fromCallable(
                                    () ->
                                            new BenchmarkReport(
                                                    agentName,
                                                    List.copyOf(roundResults),
                                                    Instant.now())));
                });
    }

    private <C> Mono<RoundResult> runRound(
            List<C> challenges,
            Function<C, Mono<ChallengeResult>> challengeRunner,
            int roundNumber) {

        return Mono.defer(
                () -> {
                    Instant start = Instant.now();
                    List<ChallengeResult> results = Collections.synchronizedList(new ArrayList<>());

                    Mono<Void> chain = Mono.empty();
                    for (C challenge : challenges) {
                        chain =
                                chain.then(
                                        challengeRunner
                                                .apply(challenge)
                                                .onErrorResume(
                                                        e ->
                                                                Mono.just(
                                                                        ChallengeResult.failure(
                                                                                challenge
                                                                                        .toString(),
                                                                                e.getMessage())))
                                                .doOnNext(results::add)
                                                .then());
                    }

                    return chain.then(
                            Mono.defer(
                                    () -> {
                                        Duration elapsed = Duration.between(start, Instant.now());
                                        long passed =
                                                results.stream()
                                                        .filter(ChallengeResult::passed)
                                                        .count();
                                        double successRate =
                                                challenges.isEmpty()
                                                        ? 0.0
                                                        : (double) passed / challenges.size();

                                        RoundResult roundResult =
                                                new RoundResult(
                                                        roundNumber,
                                                        List.copyOf(results),
                                                        successRate,
                                                        elapsed);

                                        log.info(
                                                "Benchmark round {}/{}: {}/{} passed ({}%)",
                                                roundNumber,
                                                rounds,
                                                passed,
                                                challenges.size(),
                                                String.format("%.1f", successRate * 100));

                                        if (roundNumber < rounds) {
                                            return triggerEvolution(results)
                                                    .thenReturn(roundResult);
                                        }
                                        return Mono.just(roundResult);
                                    }));
                });
    }

    private Mono<Void> triggerEvolution(List<ChallengeResult> results) {
        List<ChallengeResult> failures = results.stream().filter(r -> !r.passed()).toList();

        if (failures.isEmpty()) {
            log.info("All challenges passed; skipping evolution trigger");
            return Mono.empty();
        }

        List<Msg> failureHistory =
                failures.stream()
                        .map(
                                f ->
                                        Msg.of(
                                                MsgRole.ASSISTANT,
                                                "FAILED: "
                                                        + f.challengeId()
                                                        + " — "
                                                        + f.errorMessage()))
                        .toList();

        List<EvolvedSkill> existingSkills = skillStore.list().collectList().block();

        EvolutionContext ctx =
                new EvolutionContext(
                        agentName,
                        failureHistory,
                        failures.size(),
                        EvolutionCounters.ZERO,
                        10,
                        5,
                        0,
                        existingSkills != null ? existingSkills : List.of());

        return orchestrator.submit(ctx);
    }

    /** Result of a single challenge execution. */
    public record ChallengeResult(String challengeId, boolean passed, String errorMessage) {

        public static ChallengeResult success(String challengeId) {
            return new ChallengeResult(challengeId, true, "");
        }

        public static ChallengeResult failure(String challengeId, String errorMessage) {
            return new ChallengeResult(
                    challengeId, false, errorMessage != null ? errorMessage : "");
        }
    }

    /** Result of a single benchmark round. */
    public record RoundResult(
            int roundNumber, List<ChallengeResult> results, double successRate, Duration elapsed) {

        public RoundResult {
            results = List.copyOf(results);
        }

        public long passedCount() {
            return results.stream().filter(ChallengeResult::passed).count();
        }

        public long failedCount() {
            return results.stream().filter(r -> !r.passed()).count();
        }
    }

    /** Full benchmark report across all rounds. */
    public record BenchmarkReport(String agentName, List<RoundResult> rounds, Instant completedAt) {

        public BenchmarkReport {
            rounds = List.copyOf(rounds);
        }

        public double improvementPercent() {
            if (rounds.size() < 2) return 0.0;
            double first = rounds.get(0).successRate();
            double last = rounds.get(rounds.size() - 1).successRate();
            return (last - first) * 100.0;
        }

        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Benchmark Report for '").append(agentName).append("'\n");
            sb.append("Rounds: ").append(rounds.size()).append("\n");
            for (RoundResult r : rounds) {
                sb.append(
                        String.format(
                                "  Round %d: %.1f%% (%d/%d) in %ds%n",
                                r.roundNumber(),
                                r.successRate() * 100,
                                r.passedCount(),
                                r.results().size(),
                                r.elapsed().toSeconds()));
            }
            sb.append(String.format("Improvement: %+.1f%%\n", improvementPercent()));
            return sb.toString();
        }
    }
}
