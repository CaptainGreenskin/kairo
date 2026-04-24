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

/**
 * Lifecycle event types emitted by a {@link TeamCoordinator}.
 *
 * <p>Parallel to {@code io.kairo.api.execution.ExecutionEventType} and {@code
 * io.kairo.evolution.event.EvolutionEventType}: the three enums are peers that live in their own
 * domains and are published through the unified {@code KairoEventBus} facade. This enum never
 * extends or subtypes the other two (ADR-015 §"Event domain").
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Team event type enum; introduced in v0.10, targeting stabilization in v1.1")
public enum TeamEventType {

    /** The team has accepted a request and begun orchestrating. */
    TEAM_STARTED,

    /** A step has been assigned to a role-bound agent. */
    STEP_ASSIGNED,

    /** A step completed (before evaluation). */
    STEP_COMPLETED,

    /** Evaluation of a step artifact has begun. */
    EVALUATION_STARTED,

    /** An {@link EvaluationVerdict} was produced for a step artifact. */
    EVALUATION_RESULT,

    /** Control passed from one role to another (see {@link HandoffMessage}). */
    HANDOFF,

    /** The team breached its {@link TeamConfig#teamTimeout()}. */
    TEAM_TIMEOUT,

    /** The team reached a successful terminal state. */
    TEAM_COMPLETED,

    /** The team reached a failed terminal state. */
    TEAM_FAILED
}
