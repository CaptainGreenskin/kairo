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
package io.kairo.multiagent.team.internal;

import io.kairo.api.team.TeamStep;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Package-private DAG-based task board used by {@code DefaultTaskDispatchCoordinator} to
 * choreograph step execution.
 *
 * <p>ADR-015 retires the public {@code TaskBoard} SPI; this class is an <em>implementation
 * detail</em> of the dispatch coordinator and intentionally lives outside the public API surface.
 * No external caller should depend on it.
 *
 * <p>Semantics:
 *
 * <ul>
 *   <li>Every {@link TeamStep} starts in {@link State#PENDING}.
 *   <li>A step is "ready" when it is {@link State#PENDING} and all {@link TeamStep#dependsOn()}
 *       ancestors are {@link State#COMPLETED}.
 *   <li>{@link #markInFlight(String)} and {@link #markCompleted(String, String)} transition a step.
 *   <li>{@link #markSkipped(String, String)} is a terminal-no-artifact transition used when
 *       generator retries are exhausted but the coordinator chooses to continue rather than abort.
 *   <li>{@link #markFailed(String)} is a hard terminal failure that should cause the coordinator to
 *       abort.
 * </ul>
 *
 * <p>Thread-safe via synchronized methods — callers dispatch off separate Reactor schedulers so the
 * contention is minimal and clarity outweighs lock-free trickery.
 */
public final class InternalTaskBoard {

    /** State of a step within the board. */
    public enum State {
        PENDING,
        IN_FLIGHT,
        COMPLETED,
        SKIPPED,
        FAILED
    }

    /** Aggregate view of a step for observability and result assembly. */
    public record StepRecord(
            TeamStep step, State state, String artifact, int attempts, String failureReason) {

        public StepRecord {
            Objects.requireNonNull(step, "step must not be null");
            Objects.requireNonNull(state, "state must not be null");
            // artifact, failureReason may be null; attempts >= 0
            if (attempts < 0) {
                throw new IllegalArgumentException("attempts must be >= 0, got " + attempts);
            }
        }
    }

    private final Map<String, TeamStep> stepsById;
    private final Map<String, State> state;
    private final Map<String, String> artifacts = new HashMap<>();
    private final Map<String, Integer> attempts = new HashMap<>();
    private final Map<String, String> failureReasons = new HashMap<>();

    public InternalTaskBoard(List<TeamStep> steps) {
        Objects.requireNonNull(steps, "steps must not be null");
        this.stepsById = new LinkedHashMap<>();
        this.state = new HashMap<>();
        for (TeamStep step : steps) {
            this.stepsById.put(step.stepId(), step);
            this.state.put(step.stepId(), State.PENDING);
            this.attempts.put(step.stepId(), 0);
        }
        validateDependencies();
    }

    private void validateDependencies() {
        for (TeamStep step : stepsById.values()) {
            for (String depId : step.dependsOn()) {
                if (!stepsById.containsKey(depId)) {
                    throw new IllegalArgumentException(
                            "Step '"
                                    + step.stepId()
                                    + "' depends on unknown step id '"
                                    + depId
                                    + "'");
                }
            }
        }
    }

    /** Steps whose dependencies are all {@link State#COMPLETED} and that are still PENDING. */
    public synchronized List<TeamStep> readySteps() {
        List<TeamStep> ready = new ArrayList<>();
        for (TeamStep step : stepsById.values()) {
            if (state.get(step.stepId()) != State.PENDING) {
                continue;
            }
            if (allDependenciesCompleted(step)) {
                ready.add(step);
            }
        }
        return ready;
    }

    private boolean allDependenciesCompleted(TeamStep step) {
        for (String dep : step.dependsOn()) {
            if (state.get(dep) != State.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    public synchronized void markInFlight(String stepId) {
        requireKnown(stepId);
        state.put(stepId, State.IN_FLIGHT);
        attempts.merge(stepId, 1, Integer::sum);
    }

    public synchronized void markCompleted(String stepId, String artifact) {
        requireKnown(stepId);
        state.put(stepId, State.COMPLETED);
        artifacts.put(stepId, Objects.requireNonNullElse(artifact, ""));
    }

    public synchronized void markSkipped(String stepId, String reason) {
        requireKnown(stepId);
        state.put(stepId, State.SKIPPED);
        failureReasons.put(stepId, Objects.requireNonNullElse(reason, ""));
    }

    public synchronized void markFailed(String stepId) {
        requireKnown(stepId);
        state.put(stepId, State.FAILED);
    }

    /** Roll back an IN_FLIGHT step to PENDING so it can be retried; preserves attempt count. */
    public synchronized void markPending(String stepId) {
        requireKnown(stepId);
        state.put(stepId, State.PENDING);
    }

    public synchronized void recordFailure(String stepId, String reason) {
        requireKnown(stepId);
        failureReasons.put(stepId, Objects.requireNonNullElse(reason, ""));
    }

    public synchronized State stateOf(String stepId) {
        requireKnown(stepId);
        return state.get(stepId);
    }

    public synchronized int attemptsOf(String stepId) {
        requireKnown(stepId);
        return attempts.getOrDefault(stepId, 0);
    }

    public synchronized Collection<TeamStep> allSteps() {
        return List.copyOf(stepsById.values());
    }

    public synchronized List<StepRecord> snapshot() {
        List<StepRecord> out = new ArrayList<>(stepsById.size());
        for (TeamStep step : stepsById.values()) {
            out.add(
                    new StepRecord(
                            step,
                            state.get(step.stepId()),
                            artifacts.get(step.stepId()),
                            attempts.getOrDefault(step.stepId(), 0),
                            failureReasons.get(step.stepId())));
        }
        return out;
    }

    public synchronized boolean allTerminal() {
        for (State s : state.values()) {
            if (s == State.PENDING || s == State.IN_FLIGHT) {
                return false;
            }
        }
        return true;
    }

    public synchronized boolean anyFailed() {
        return state.values().contains(State.FAILED);
    }

    public synchronized int inFlightCount() {
        int count = 0;
        for (State s : state.values()) {
            if (s == State.IN_FLIGHT) {
                count++;
            }
        }
        return count;
    }

    /** Identifiers of every step not in a terminal state (still PENDING or IN_FLIGHT). */
    public synchronized Set<String> nonTerminalStepIds() {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, State> e : state.entrySet()) {
            if (e.getValue() == State.PENDING || e.getValue() == State.IN_FLIGHT) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    private void requireKnown(String stepId) {
        if (!stepsById.containsKey(stepId)) {
            throw new IllegalArgumentException("Unknown step id: " + stepId);
        }
    }
}
