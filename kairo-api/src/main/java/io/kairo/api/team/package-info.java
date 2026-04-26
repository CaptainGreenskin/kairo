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
 * Expert-team orchestration SPI surface.
 *
 * <p>This package hosts the v0.10 Expert Team contracts that supersede the legacy {@code
 * TeamScheduler} / {@code TaskBoard} surface:
 *
 * <ul>
 *   <li>{@link io.kairo.api.team.TeamCoordinator} — drives a team end-to-end (ADR-016).
 *   <li>{@link io.kairo.api.team.EvaluationStrategy} — judges step artifacts (ADR-016).
 *   <li>Value objects ({@link io.kairo.api.team.TeamExecutionRequest}, {@link
 *       io.kairo.api.team.TeamExecutionPlan}, {@link io.kairo.api.team.TeamStep}, {@link
 *       io.kairo.api.team.RoleDefinition}, {@link io.kairo.api.team.EvaluationVerdict}, {@link
 *       io.kairo.api.team.EvaluationContext}, {@link io.kairo.api.team.HandoffMessage}, {@link
 *       io.kairo.api.team.TeamConfig}, {@link io.kairo.api.team.TeamResult}, {@link
 *       io.kairo.api.team.TeamResourceConstraint}).
 *   <li>Enums ({@link io.kairo.api.team.RiskProfile}, {@link io.kairo.api.team.TeamStatus}, {@link
 *       io.kairo.api.team.EvaluatorPreference}, {@link io.kairo.api.team.PlannerFailureMode},
 *       {@link io.kairo.api.team.TeamEventType}).
 *   <li>Event bridge ({@link io.kairo.api.team.TeamEvent}) — peer of {@code ExecutionEvent} and the
 *       evolution event record, published through the unified {@code KairoEventBus} facade.
 * </ul>
 *
 * <p>All value objects in this package are immutable records with constructor-validated arguments.
 * Null parameters and blank identifiers are rejected at the boundary.
 *
 * @see <a href="../../../../../../docs/adr/ADR-015-expert-team-orchestration.md">ADR-015 Expert
 *     Team Orchestration</a>
 * @see <a href="../../../../../../docs/adr/ADR-016-coordinator-spi.md">ADR-016 Coordinator SPI</a>
 * @since v0.10 (Experimental)
 */
package io.kairo.api.team;
