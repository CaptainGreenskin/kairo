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

import io.kairo.api.team.TeamStep;
import io.kairo.expertteam.strategy.ArchitectArbitrator;
import io.kairo.expertteam.strategy.ScoredArtifact;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Executes a DAG of {@link TeamStep}s layer-by-layer with bounded concurrency.
 *
 * <p>Uses topological sort (Kahn's algorithm) to group steps into dependency layers, then executes
 * each layer with {@code Flux.flatMap(fn, maxConcurrency)}. Steps within the same layer have no
 * mutual dependencies and can safely execute in parallel.
 *
 * <p>Error semantics: fail-fast. If any step in a layer fails (returns {@code Mono.error}), the
 * remaining steps in that layer are cancelled and the error propagates immediately.
 *
 * @since v0.10 (Experimental)
 */
public class DagExecutor {

    private final int maxConcurrency;
    @Nullable private final ArchitectArbitrator arbitrator;
    @Nullable private final String goal;

    private static final int DEFAULT_MAX_CONCURRENCY =
            Integer.parseInt(System.getenv().getOrDefault("KAIRO_EXPERT_MAX_CONCURRENCY", "4"));

    /**
     * Creates an executor with the default concurrency (env {@code KAIRO_EXPERT_MAX_CONCURRENCY} or
     * 4).
     */
    public DagExecutor() {
        this(DEFAULT_MAX_CONCURRENCY, null, null);
    }

    /**
     * Creates an executor with the specified bounded concurrency.
     *
     * @param maxConcurrency maximum number of steps executed in parallel within a single layer
     * @throws IllegalArgumentException if {@code maxConcurrency < 1}
     */
    public DagExecutor(int maxConcurrency) {
        this(maxConcurrency, null, null);
    }

    /**
     * Creates an executor with the specified bounded concurrency and optional architect arbitrator.
     *
     * @param maxConcurrency maximum number of steps executed in parallel within a single layer
     * @param arbitrator optional architect arbitrator for score divergence resolution; may be
     *     {@code null}
     * @param goal the team goal context for arbitration prompts; may be {@code null}
     * @throws IllegalArgumentException if {@code maxConcurrency < 1}
     */
    public DagExecutor(
            int maxConcurrency, @Nullable ArchitectArbitrator arbitrator, @Nullable String goal) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException(
                    "maxConcurrency must be >= 1, got " + maxConcurrency);
        }
        this.maxConcurrency = maxConcurrency;
        this.arbitrator = arbitrator;
        this.goal = goal;
    }

    /**
     * Execute all steps in DAG order. Steps with satisfied dependencies run in parallel up to
     * {@code maxConcurrency} within each layer.
     *
     * @param steps the full DAG of steps (must be acyclic)
     * @param executor function that executes a single step and returns its outcome
     * @param <T> the outcome type produced by each step
     * @return ordered list of outcomes in topological (layer) order
     * @throws IllegalArgumentException if the DAG contains cycles or references unknown step IDs
     */
    public <T> Mono<List<T>> execute(List<TeamStep> steps, Function<TeamStep, Mono<T>> executor) {
        if (steps == null || steps.isEmpty()) {
            return Mono.just(List.of());
        }
        List<List<TeamStep>> layers = topoSort(steps);
        return executeLayers(layers, executor);
    }

    /**
     * Topological sort into layers using Kahn's algorithm.
     *
     * <p>Steps with no unresolved dependencies form the first layer; subsequent layers contain
     * steps whose dependencies are all satisfied by prior layers.
     *
     * @param steps the DAG steps
     * @return ordered list of layers (each layer is a list of steps that can run in parallel)
     * @throws IllegalArgumentException if the DAG contains cycles or references unknown step IDs
     */
    List<List<TeamStep>> topoSort(List<TeamStep> steps) {
        // Build lookup: stepId → TeamStep
        Map<String, TeamStep> stepById = new HashMap<>(steps.size());
        for (TeamStep step : steps) {
            if (stepById.containsKey(step.stepId())) {
                throw new IllegalArgumentException("Duplicate stepId: " + step.stepId());
            }
            stepById.put(step.stepId(), step);
        }

        // Validate all dependsOn references point to known steps
        for (TeamStep step : steps) {
            for (String dep : step.dependsOn()) {
                if (!stepById.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            "Step '" + step.stepId() + "' depends on unknown stepId '" + dep + "'");
                }
            }
        }

        // Build in-degree map
        Map<String, Integer> inDegree = new HashMap<>(steps.size());
        for (TeamStep step : steps) {
            inDegree.put(step.stepId(), step.dependsOn().size());
        }

        // Build reverse adjacency: dependency → set of dependents
        Map<String, Set<String>> dependents = new HashMap<>();
        for (TeamStep step : steps) {
            for (String dep : step.dependsOn()) {
                dependents.computeIfAbsent(dep, k -> new HashSet<>()).add(step.stepId());
            }
        }

        // Kahn's algorithm with layer tracking
        List<List<TeamStep>> layers = new ArrayList<>();
        Queue<String> currentLayer = new ArrayDeque<>();

        // Seed with all steps that have in-degree 0
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                currentLayer.add(entry.getKey());
            }
        }

        int processedCount = 0;
        while (!currentLayer.isEmpty()) {
            List<TeamStep> layer = new ArrayList<>(currentLayer.size());
            Queue<String> nextLayer = new ArrayDeque<>();

            while (!currentLayer.isEmpty()) {
                String stepId = currentLayer.poll();
                layer.add(stepById.get(stepId));
                processedCount++;

                // Decrement in-degree of all dependents
                Set<String> deps = dependents.getOrDefault(stepId, Set.of());
                for (String dependent : deps) {
                    int newDegree = inDegree.compute(dependent, (k, v) -> v - 1);
                    if (newDegree == 0) {
                        nextLayer.add(dependent);
                    }
                }
            }

            layers.add(layer);
            currentLayer = nextLayer;
        }

        if (processedCount != steps.size()) {
            throw new IllegalArgumentException(
                    "DAG contains a cycle; "
                            + (steps.size() - processedCount)
                            + " step(s) could not be topologically sorted");
        }

        return layers;
    }

    /** Returns the configured max concurrency. */
    int maxConcurrency() {
        return maxConcurrency;
    }

    /** Returns the optional architect arbitrator. */
    @Nullable
    public ArchitectArbitrator arbitrator() {
        return arbitrator;
    }

    /** Returns the goal context for arbitration. */
    @Nullable
    public String goal() {
        return goal;
    }

    /**
     * Check whether a set of scored artifacts should trigger architect arbitration.
     *
     * <p>Delegates to the arbitrator's threshold check. Returns {@code false} if no arbitrator is
     * configured.
     *
     * @param artifacts the scored artifacts from a parallel layer
     * @return {@code true} if arbitration should be triggered
     */
    public boolean shouldArbitrate(List<ScoredArtifact> artifacts) {
        return arbitrator != null && arbitrator.shouldArbitrate(artifacts);
    }

    // ---------------------------------------------------------------------- private

    private <T> Mono<List<T>> executeLayers(
            List<List<TeamStep>> layers, Function<TeamStep, Mono<T>> executor) {
        Mono<List<T>> pipeline = Mono.just(new ArrayList<>());

        for (List<TeamStep> layer : layers) {
            pipeline =
                    pipeline.flatMap(
                            accumulated ->
                                    Flux.fromIterable(layer)
                                            .flatMap(executor::apply, maxConcurrency)
                                            .collectList()
                                            .map(
                                                    layerResults -> {
                                                        List<T> merged =
                                                                new ArrayList<>(accumulated);
                                                        merged.addAll(layerResults);
                                                        return merged;
                                                    }));
        }

        return pipeline;
    }
}
