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

/**
 * Expert-team orchestration module.
 *
 * <p>Concrete implementations of the {@link io.kairo.api.team.TeamCoordinator} and {@link
 * io.kairo.api.team.EvaluationStrategy} SPIs that ship the plan → generate → evaluate → feedback
 * loop described in ADR-015. The module depends only on {@code kairo-api} and {@code kairo-core};
 * callers exclude it wholesale when they do not want expert-team orchestration on the classpath.
 *
 * <p>Public types:
 *
 * <ul>
 *   <li>{@link io.kairo.expertteam.ExpertTeamCoordinator} — the default plan-act-evaluate
 *       coordinator.
 *   <li>{@link io.kairo.expertteam.SimpleEvaluationStrategy} — deterministic rubric evaluator.
 *   <li>{@link io.kairo.expertteam.AgentEvaluationStrategy} — LLM-judge evaluator.
 *   <li>{@link io.kairo.expertteam.ExpertTeamStateMachine} — canonical state/transition validator.
 * </ul>
 *
 * <p>The {@code io.kairo.expertteam.tck} sub-package is a Technology Compatibility Kit — abstract
 * JUnit 5 classes that third-party {@link io.kairo.api.team.TeamCoordinator} and {@link
 * io.kairo.api.team.EvaluationStrategy} implementations extend to validate contract compliance.
 * Shipped under {@code src/main/java/} (rather than as a {@code test-jar}) so consumers do not need
 * Maven test-jar wiring; JUnit / AssertJ / Reactor-test are declared as optional compile-scope
 * dependencies.
 *
 * @see <a href="../../../../../../docs/adr/ADR-015-expert-team-orchestration.md">ADR-015 Expert
 *     Team Orchestration</a>
 * @see <a href="../../../../../../docs/adr/ADR-016-coordinator-spi.md">ADR-016 Coordinator SPI</a>
 * @since v0.10 (Experimental)
 */
package io.kairo.expertteam;
