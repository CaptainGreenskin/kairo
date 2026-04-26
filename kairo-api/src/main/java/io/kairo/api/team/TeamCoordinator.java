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
package io.kairo.api.team;

import io.kairo.api.Experimental;
import reactor.core.publisher.Mono;

/**
 * Drives a {@link Team} end-to-end for a single {@link TeamExecutionRequest}.
 *
 * <p>A coordinator owns the full choreography between the moment a caller hands off a request and
 * the moment a {@link TeamResult} is produced. Contract-level failure semantics (planner fail-fast,
 * evaluator {@code REVIEW_EXCEEDED}, timeout with partial result, etc.) are fixed by ADR-015;
 * configurable details flow through {@link TeamConfig}.
 *
 * <p>Two reference implementations ship with v0.10:
 *
 * <ul>
 *   <li>{@code DefaultTaskDispatchCoordinator} in {@code kairo-multi-agent} — task-board dispatch
 *       semantics (the former {@code TeamScheduler} behaviour).
 *   <li>{@code ExpertTeamCoordinator} in {@code kairo-expert-team} — plan ➜ generate ➜ evaluate
 *       loop.
 * </ul>
 *
 * <p>Third-party implementors must pass the {@code TeamCoordinatorTCK} (ADR-016).
 *
 * @see EvaluationStrategy
 * @see <a href="../../../../../../docs/adr/ADR-015-expert-team-orchestration.md">ADR-015</a>
 * @see <a href="../../../../../../docs/adr/ADR-016-coordinator-spi.md">ADR-016</a>
 * @since v0.10 (Experimental)
 */
@Experimental("TeamCoordinator SPI — contract may change in v0.11")
public interface TeamCoordinator {

    /**
     * Execute the given request against the supplied team.
     *
     * <p>Implementations MUST emit lifecycle events via the unified event bus and MUST honour the
     * failure semantics documented in ADR-015 (§"Failure semantics"). Timeouts must cancel
     * in-flight work and return a partial {@link TeamResult} rather than hang.
     *
     * @param request the execution request; never {@code null}
     * @param team the bound team to execute against; never {@code null}
     * @return a {@link Mono} emitting the terminal {@link TeamResult}; never completes empty
     */
    Mono<TeamResult> execute(TeamExecutionRequest request, Team team);
}
