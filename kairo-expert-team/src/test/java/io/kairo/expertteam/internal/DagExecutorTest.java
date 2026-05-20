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
package io.kairo.expertteam.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.TeamStep;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests for {@link DagExecutor}. */
class DagExecutorTest {

    private static final RoleDefinition ROLE =
            new RoleDefinition("coder", "Coder", "Write code", "coding", List.of());

    private final DagExecutor executor = new DagExecutor(4);

    // ---------------------------------------------------------------------- helper

    private static TeamStep step(String id, int index, String... deps) {
        return new TeamStep(id, "Step " + id, ROLE, List.of(deps), index);
    }

    // ---------------------------------------------------------------------- basic tests

    @Test
    void emptyDagReturnsEmptyList() {
        StepVerifier.create(executor.execute(List.of(), step -> Mono.just(step.stepId())))
                .expectNext(List.of())
                .verifyComplete();
    }

    @Test
    void singleStepExecutes() {
        TeamStep a = step("A", 0);
        Mono<List<String>> result = executor.execute(List.of(a), s -> Mono.just(s.stepId()));

        StepVerifier.create(result)
                .expectNextMatches(list -> list.equals(List.of("A")))
                .verifyComplete();
    }

    @Test
    void linearChainExecutesInOrder() {
        // A → B → C (linear dependency chain)
        TeamStep a = step("A", 0);
        TeamStep b = step("B", 1, "A");
        TeamStep c = step("C", 2, "B");

        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        Mono<List<String>> result =
                executor.execute(
                        List.of(a, b, c),
                        s -> {
                            executionOrder.add(s.stepId());
                            return Mono.just(s.stepId());
                        });

        StepVerifier.create(result).expectNextMatches(list -> list.size() == 3).verifyComplete();

        // Execution order must respect dependencies
        assertThat(executionOrder.indexOf("A")).isLessThan(executionOrder.indexOf("B"));
        assertThat(executionOrder.indexOf("B")).isLessThan(executionOrder.indexOf("C"));
    }

    @Test
    void twoParallelStepsInSameLayer() {
        // A and B have no dependencies → same layer → executed concurrently
        TeamStep a = step("A", 0);
        TeamStep b = step("B", 1);

        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch proceed = new CountDownLatch(1);

        Mono<List<String>> result =
                executor.execute(
                        List.of(a, b),
                        s ->
                                Mono.defer(
                                                () -> {
                                                    bothStarted.countDown();
                                                    try {
                                                        // Both must start before either can
                                                        // complete
                                                        bothStarted.await(2, TimeUnit.SECONDS);
                                                        proceed.await(2, TimeUnit.SECONDS);
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                    }
                                                    return Mono.just(s.stepId());
                                                })
                                        .subscribeOn(reactor.core.scheduler.Schedulers.parallel()));

        // Release the latch after a small delay
        new Thread(
                        () -> {
                            try {
                                bothStarted.await(2, TimeUnit.SECONDS);
                                proceed.countDown();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        })
                .start();

        StepVerifier.create(result)
                .expectNextMatches(
                        list -> list.size() == 2 && list.contains("A") && list.contains("B"))
                .verifyComplete();
    }

    @Test
    void diamondPatternExecutesCorrectly() {
        // Diamond: A → B, A → C, B → D, C → D
        // Layer 0: [A], Layer 1: [B, C], Layer 2: [D]
        TeamStep a = step("A", 0);
        TeamStep b = step("B", 1, "A");
        TeamStep c = step("C", 2, "A");
        TeamStep d = step("D", 3, "B", "C");

        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        Mono<List<String>> result =
                executor.execute(
                        List.of(a, b, c, d),
                        s -> {
                            executionOrder.add(s.stepId());
                            return Mono.just(s.stepId());
                        });

        StepVerifier.create(result).expectNextMatches(list -> list.size() == 4).verifyComplete();

        // A must execute before B and C; B and C must execute before D
        assertThat(executionOrder.indexOf("A")).isLessThan(executionOrder.indexOf("B"));
        assertThat(executionOrder.indexOf("A")).isLessThan(executionOrder.indexOf("C"));
        assertThat(executionOrder.indexOf("B")).isLessThan(executionOrder.indexOf("D"));
        assertThat(executionOrder.indexOf("C")).isLessThan(executionOrder.indexOf("D"));
    }

    // ---------------------------------------------------------------------- topoSort unit tests

    @Test
    void topoSortGroupsIndependentStepsInSameLayer() {
        TeamStep a = step("A", 0);
        TeamStep b = step("B", 1);
        TeamStep c = step("C", 2, "A", "B");

        List<List<TeamStep>> layers = executor.topoSort(List.of(a, b, c));

        assertThat(layers).hasSize(2);
        // Layer 0 contains A and B (independent)
        assertThat(layers.get(0)).extracting(TeamStep::stepId).containsExactlyInAnyOrder("A", "B");
        // Layer 1 contains C (depends on both)
        assertThat(layers.get(1)).extracting(TeamStep::stepId).containsExactly("C");
    }

    @Test
    void topoSortLinearChainProducesSingleElementLayers() {
        TeamStep a = step("A", 0);
        TeamStep b = step("B", 1, "A");
        TeamStep c = step("C", 2, "B");

        List<List<TeamStep>> layers = executor.topoSort(List.of(a, b, c));

        assertThat(layers).hasSize(3);
        assertThat(layers.get(0)).extracting(TeamStep::stepId).containsExactly("A");
        assertThat(layers.get(1)).extracting(TeamStep::stepId).containsExactly("B");
        assertThat(layers.get(2)).extracting(TeamStep::stepId).containsExactly("C");
    }

    // ---------------------------------------------------------------------- error cases

    @Test
    void cycleDetectionThrows() {
        // A → B → A (cycle)
        TeamStep a = step("A", 0, "B");
        TeamStep b = step("B", 1, "A");

        assertThatThrownBy(() -> executor.topoSort(List.of(a, b)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void selfDependencyCycleDetected() {
        // A depends on itself
        TeamStep a = step("A", 0, "A");

        assertThatThrownBy(() -> executor.topoSort(List.of(a)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void threeNodeCycleDetected() {
        // A → B → C → A
        TeamStep a = step("A", 0, "C");
        TeamStep b = step("B", 1, "A");
        TeamStep c = step("C", 2, "B");

        assertThatThrownBy(() -> executor.topoSort(List.of(a, b, c)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void unknownDependencyThrows() {
        TeamStep a = step("A", 0, "UNKNOWN");

        assertThatThrownBy(() -> executor.topoSort(List.of(a)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown stepId 'UNKNOWN'");
    }

    @Test
    void duplicateStepIdThrows() {
        TeamStep a1 = step("A", 0);
        TeamStep a2 = step("A", 1);

        assertThatThrownBy(() -> executor.topoSort(List.of(a1, a2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate stepId");
    }

    // ---------------------------------------------------------------------- step failure

    @Test
    void stepFailurePropagates() {
        TeamStep a = step("A", 0);
        TeamStep b = step("B", 1, "A");
        RuntimeException boom = new RuntimeException("step A exploded");

        Mono<List<String>> result =
                executor.execute(
                        List.of(a, b),
                        s -> {
                            if ("A".equals(s.stepId())) {
                                return Mono.error(boom);
                            }
                            return Mono.just(s.stepId());
                        });

        StepVerifier.create(result).expectErrorMatches(ex -> ex == boom).verify();
    }

    @Test
    void failureInSecondLayerPropagates() {
        TeamStep a = step("A", 0);
        TeamStep b = step("B", 1, "A");
        RuntimeException boom = new RuntimeException("step B failed");

        Mono<List<String>> result =
                executor.execute(
                        List.of(a, b),
                        s -> {
                            if ("B".equals(s.stepId())) {
                                return Mono.error(boom);
                            }
                            return Mono.just(s.stepId());
                        });

        StepVerifier.create(result).expectErrorMatches(ex -> ex == boom).verify();
    }

    // ---------------------------------------------------------------------- concurrency control

    @Test
    void maxConcurrencyIsRespected() {
        // Create 8 independent steps (all in layer 0) with maxConcurrency = 2
        DagExecutor boundedExecutor = new DagExecutor(2);
        List<TeamStep> steps = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            steps.add(step("S" + i, i));
        }

        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger maxObserved = new AtomicInteger(0);

        Mono<List<String>> result =
                boundedExecutor.execute(
                        steps,
                        s ->
                                Mono.defer(
                                        () -> {
                                            int c = concurrent.incrementAndGet();
                                            maxObserved.updateAndGet(prev -> Math.max(prev, c));
                                            return Mono.delay(Duration.ofMillis(50))
                                                    .doOnNext(x -> concurrent.decrementAndGet())
                                                    .map(x -> s.stepId());
                                        }));

        StepVerifier.create(result).expectNextMatches(list -> list.size() == 8).verifyComplete();

        // With flatMap bounded concurrency = 2, max observed should be <= 2
        assertThat(maxObserved.get()).isLessThanOrEqualTo(2);
    }

    // ---------------------------------------------------------------------- constructor validation

    @Test
    void invalidMaxConcurrencyThrows() {
        assertThatThrownBy(() -> new DagExecutor(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrency must be >= 1");

        assertThatThrownBy(() -> new DagExecutor(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrency must be >= 1");
    }

    @Test
    void defaultConstructorUsesConfiguredConcurrency() {
        DagExecutor defaultExecutor = new DagExecutor();
        assertThat(defaultExecutor.maxConcurrency()).isGreaterThanOrEqualTo(1);
    }

    // ---------------------------------------------------------------------- complex DAG

    @Test
    void complexDagWithMultipleLayers() {
        // Layer 0: A, B
        // Layer 1: C (depends on A), D (depends on B)
        // Layer 2: E (depends on C, D)
        // Layer 3: F (depends on E)
        TeamStep a = step("A", 0);
        TeamStep b = step("B", 1);
        TeamStep c = step("C", 2, "A");
        TeamStep d = step("D", 3, "B");
        TeamStep e = step("E", 4, "C", "D");
        TeamStep f = step("F", 5, "E");

        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());

        Mono<List<String>> result =
                executor.execute(
                        List.of(a, b, c, d, e, f),
                        s -> {
                            executionOrder.add(s.stepId());
                            return Mono.just(s.stepId());
                        });

        StepVerifier.create(result).expectNextMatches(list -> list.size() == 6).verifyComplete();

        // Verify all dependency constraints
        assertThat(executionOrder.indexOf("A")).isLessThan(executionOrder.indexOf("C"));
        assertThat(executionOrder.indexOf("B")).isLessThan(executionOrder.indexOf("D"));
        assertThat(executionOrder.indexOf("C")).isLessThan(executionOrder.indexOf("E"));
        assertThat(executionOrder.indexOf("D")).isLessThan(executionOrder.indexOf("E"));
        assertThat(executionOrder.indexOf("E")).isLessThan(executionOrder.indexOf("F"));
    }

    @Test
    void nullStepsReturnsEmptyList() {
        StepVerifier.create(executor.execute(null, s -> Mono.just(s.stepId())))
                .expectNext(List.of())
                .verifyComplete();
    }
}
