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

/**
 * Deterministic state machine governing evolution pipeline transitions.
 *
 * <p>Validates that transitions follow the allowed state graph and throws {@link
 * IllegalStateException} for invalid transitions.
 *
 * @since v0.9 (Experimental)
 */
public final class EvolutionStateMachine {

    private final int maxConsecutiveFailures;

    /**
     * Create a state machine with the given failure threshold.
     *
     * @param maxConsecutiveFailures max consecutive retryable failures before suspending
     */
    public EvolutionStateMachine(int maxConsecutiveFailures) {
        if (maxConsecutiveFailures < 1) {
            throw new IllegalArgumentException("maxConsecutiveFailures must be >= 1");
        }
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    /**
     * Compute the next state given the current state and an incoming signal.
     *
     * @param from the current state
     * @param signal the incoming signal
     * @return the next state
     * @throws IllegalStateException if the transition is not allowed
     */
    public EvolutionState transit(EvolutionState from, EvolutionSignal signal) {
        return switch (from) {
            case IDLE ->
                    switch (signal) {
                        case START_REVIEW -> EvolutionState.REVIEWING;
                        default -> invalid(from, signal);
                    };
            case REVIEWING ->
                    switch (signal) {
                        case REVIEW_COMPLETE -> EvolutionState.QUARANTINED;
                        case FAILURE_RETRYABLE -> EvolutionState.FAILED_RETRYABLE;
                        case FAILURE_HARD -> EvolutionState.FAILED_HARD;
                        default -> invalid(from, signal);
                    };
            case QUARANTINED ->
                    switch (signal) {
                        case SCAN_PASS -> EvolutionState.APPLIED;
                        case SCAN_REJECT -> EvolutionState.IDLE;
                        default -> invalid(from, signal);
                    };
            case APPLIED ->
                    switch (signal) {
                        case START_REVIEW -> EvolutionState.REVIEWING;
                        default -> invalid(from, signal);
                    };
            case FAILED_RETRYABLE ->
                    switch (signal) {
                        case RETRY -> EvolutionState.REVIEWING;
                        case FAILURE_HARD -> EvolutionState.SUSPENDED;
                        default -> invalid(from, signal);
                    };
            case FAILED_HARD ->
                    switch (signal) {
                        case RESUME -> EvolutionState.IDLE;
                        default -> invalid(from, signal);
                    };
            case SUSPENDED ->
                    switch (signal) {
                        case RESUME -> EvolutionState.IDLE;
                        default -> invalid(from, signal);
                    };
        };
    }

    /**
     * Whether a RETRY from FAILED_RETRYABLE should instead suspend, based on consecutive failures.
     *
     * @param consecutiveFailures the current consecutive failure count
     * @return true if evolution should be suspended
     */
    public boolean shouldSuspend(int consecutiveFailures) {
        return consecutiveFailures >= maxConsecutiveFailures;
    }

    public int maxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    private EvolutionState invalid(EvolutionState from, EvolutionSignal signal) {
        throw new IllegalStateException("Invalid transition: " + from + " + " + signal);
    }
}
