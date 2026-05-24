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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.evolution.EvolutionOutcome;
import io.kairo.api.evolution.EvolutionPolicy;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.evolution.EvolutionBenchmarkRunner.BenchmarkReport;
import io.kairo.evolution.EvolutionBenchmarkRunner.ChallengeResult;
import io.kairo.skill.InMemoryEvolvedSkillStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

final class EvolutionBenchmarkRunnerTest {

    private EvolutionPipelineOrchestrator orchestrator;
    private EvolvedSkillStore skillStore;

    @BeforeEach
    void setUp() {
        EvolutionPolicy policy = mock(EvolutionPolicy.class);
        when(policy.review(any())).thenReturn(Mono.just(EvolutionOutcome.empty()));

        skillStore = new InMemoryEvolvedSkillStore();

        orchestrator =
                new EvolutionPipelineOrchestrator(
                        policy,
                        skillStore,
                        new EvolutionStateMachine(3),
                        new InMemoryEvolutionRuntimeStateStore());
    }

    @Test
    void allChallengesPass() {
        EvolutionBenchmarkRunner runner =
                new EvolutionBenchmarkRunner(orchestrator, skillStore, 2, "test-agent");

        List<String> challenges = List.of("challenge-1", "challenge-2");

        BenchmarkReport report =
                runner.run(challenges, c -> Mono.just(ChallengeResult.success(c))).block();

        assertThat(report).isNotNull();
        assertThat(report.rounds()).hasSize(2);
        assertThat(report.rounds().get(0).successRate()).isEqualTo(1.0);
        assertThat(report.rounds().get(1).successRate()).isEqualTo(1.0);
        assertThat(report.improvementPercent()).isEqualTo(0.0);
    }

    @Test
    void mixedResults() {
        EvolutionBenchmarkRunner runner =
                new EvolutionBenchmarkRunner(orchestrator, skillStore, 1, "test-agent");

        List<String> challenges = List.of("easy", "hard");

        BenchmarkReport report =
                runner.run(
                                challenges,
                                c -> {
                                    if (c.equals("easy")) {
                                        return Mono.just(ChallengeResult.success(c));
                                    }
                                    return Mono.just(ChallengeResult.failure(c, "Too difficult"));
                                })
                        .block();

        assertThat(report).isNotNull();
        assertThat(report.rounds()).hasSize(1);
        assertThat(report.rounds().get(0).successRate()).isEqualTo(0.5);
        assertThat(report.rounds().get(0).passedCount()).isEqualTo(1);
        assertThat(report.rounds().get(0).failedCount()).isEqualTo(1);
    }

    @Test
    void challengeRunnerExceptionBecomesFailure() {
        EvolutionBenchmarkRunner runner =
                new EvolutionBenchmarkRunner(orchestrator, skillStore, 1, "test-agent");

        BenchmarkReport report =
                runner.run(
                                List.of("boom"),
                                c -> Mono.error(new RuntimeException("Unexpected error")))
                        .block();

        assertThat(report).isNotNull();
        assertThat(report.rounds().get(0).successRate()).isEqualTo(0.0);
    }

    @Test
    void emptyChallengelist() {
        EvolutionBenchmarkRunner runner =
                new EvolutionBenchmarkRunner(orchestrator, skillStore, 1, "test-agent");

        BenchmarkReport report =
                runner.run(List.<String>of(), c -> Mono.just(ChallengeResult.success(c))).block();

        assertThat(report).isNotNull();
        assertThat(report.rounds().get(0).successRate()).isEqualTo(0.0);
    }

    @Test
    void reportSummaryContainsKey() {
        EvolutionBenchmarkRunner runner =
                new EvolutionBenchmarkRunner(orchestrator, skillStore, 2, "bench-agent");

        BenchmarkReport report =
                runner.run(List.of("task1"), c -> Mono.just(ChallengeResult.success(c))).block();

        String summary = report.toSummary();
        assertThat(summary).contains("bench-agent");
        assertThat(summary).contains("Round 1");
        assertThat(summary).contains("Round 2");
    }

    @Test
    void invalidRoundsThrows() {
        assertThatThrownBy(() -> new EvolutionBenchmarkRunner(orchestrator, skillStore, 0, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
