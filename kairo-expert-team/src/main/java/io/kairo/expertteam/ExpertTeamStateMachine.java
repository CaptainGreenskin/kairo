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
package io.kairo.expertteam;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical lifecycle state machine for an expert-team execution (ADR-015 §"Lifecycle state
 * machine").
 *
 * <p>The state machine is intentionally a validator rather than a mutable driver: the coordinator
 * keeps the authoritative current state and asks the machine whether a proposed transition is
 * legal. This keeps the coordinator's control flow explicit (each decision site names the target
 * state) while still gating invalid transitions at a single chokepoint.
 *
 * <p>Allowed transitions:
 *
 * <pre>{@code
 * IDLE       -> PLANNING
 * PLANNING   -> GENERATING | FAILED
 * GENERATING -> EVALUATING | FAILED | TIMEOUT
 * EVALUATING -> GENERATING (revise) | COMPLETED | FAILED | DEGRADED | TIMEOUT
 * COMPLETED, FAILED, DEGRADED, TIMEOUT  -> (terminal)
 * }</pre>
 *
 * @since v0.10 (Experimental)
 */
public final class ExpertTeamStateMachine {

    /** Canonical lifecycle states. */
    public enum State {

        /** Initial state before a request is accepted. */
        IDLE,

        /** Producing a {@link io.kairo.api.team.TeamExecutionPlan} from the request. */
        PLANNING,

        /** Dispatching the current step to its role-bound agent. */
        GENERATING,

        /**
         * Running the {@link io.kairo.api.team.EvaluationStrategy} against a generated artifact.
         */
        EVALUATING,

        /** All steps passed. Terminal. */
        COMPLETED,

        /** The team aborted before producing a usable result. Terminal. */
        FAILED,

        /** Best-effort success with warnings (LOW risk opt-in). Terminal. */
        DEGRADED,

        /** Overall team timeout tripped. Terminal. */
        TIMEOUT
    }

    private static final Map<State, EnumSet<State>> ALLOWED =
            Map.of(
                    State.IDLE, EnumSet.of(State.PLANNING),
                    State.PLANNING, EnumSet.of(State.GENERATING, State.FAILED),
                    State.GENERATING, EnumSet.of(State.EVALUATING, State.FAILED, State.TIMEOUT),
                    State.EVALUATING,
                            EnumSet.of(
                                    State.GENERATING,
                                    State.COMPLETED,
                                    State.FAILED,
                                    State.DEGRADED,
                                    State.TIMEOUT),
                    State.COMPLETED, EnumSet.noneOf(State.class),
                    State.FAILED, EnumSet.noneOf(State.class),
                    State.DEGRADED, EnumSet.noneOf(State.class),
                    State.TIMEOUT, EnumSet.noneOf(State.class));

    /** Whether {@code from -> to} is a legal transition. */
    public boolean canTransition(State from, State to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(State.class)).contains(to);
    }

    /** True when the given state is terminal (no outgoing transitions). */
    public boolean isTerminal(State state) {
        Objects.requireNonNull(state, "state must not be null");
        return ALLOWED.getOrDefault(state, EnumSet.noneOf(State.class)).isEmpty();
    }

    /**
     * Validate a transition; throws {@link IllegalStateException} if not allowed.
     *
     * @return {@code to} for fluent chaining.
     */
    public State transition(State from, State to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Invalid state transition: " + from + " -> " + to);
        }
        return to;
    }
}
