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
package io.kairo.core.governance;

import io.kairo.api.Experimental;
import java.util.List;

/**
 * Factory for pre-configured governance hook packs that protect agent sessions from runaway
 * behavior: excessive turns, context overflow, tool budget exhaustion, and repetitive tool loops.
 *
 * <p>Usage with AgentBuilder:
 *
 * <pre>{@code
 * Agent agent = AgentBuilder.create()
 *     .hooks(GovernanceHookPack.defaults())
 *     .build();
 * }</pre>
 *
 * <p>Three presets are provided:
 *
 * <ul>
 *   <li>{@link #defaults()} — balanced thresholds for typical sessions
 *   <li>{@link #strict()} — tighter limits for constrained environments
 *   <li>{@link #relaxed()} — looser limits for long-running interactive sessions
 * </ul>
 */
@Experimental("Governance hook pack factory; v0.12")
public final class GovernanceHookPack {

    private GovernanceHookPack() {}

    /**
     * Default governance pack: warn/force at balanced thresholds.
     *
     * <ul>
     *   <li>MaxTurns: warn=20, force=30
     *   <li>ContextSize: warn=40k, critical=70k tokens
     *   <li>ToolBudget: warn=60, force=100 calls
     *   <li>RepetitiveTool: threshold=4 consecutive
     * </ul>
     */
    public static List<Object> defaults() {
        return defaults(false);
    }

    /**
     * Default governance pack with interactive mode control.
     *
     * @param interactive true to suppress turn/tool budget guards in interactive sessions
     */
    public static List<Object> defaults(boolean interactive) {
        return List.of(
                new MaxTurnsGuard(20, 30, interactive),
                new ContextSizeGuard(40_000, 70_000, interactive),
                new ToolCallBudgetGuard(60, 100, interactive),
                new RepetitiveToolGuard(4, interactive));
    }

    /**
     * Strict governance pack: tighter limits for constrained or automated environments.
     *
     * <ul>
     *   <li>MaxTurns: warn=10, force=15
     *   <li>ContextSize: warn=25k, critical=40k tokens
     *   <li>ToolBudget: warn=30, force=50 calls
     *   <li>RepetitiveTool: threshold=3 consecutive
     * </ul>
     */
    public static List<Object> strict() {
        return strict(false);
    }

    public static List<Object> strict(boolean interactive) {
        return List.of(
                new MaxTurnsGuard(10, 15, interactive),
                new ContextSizeGuard(25_000, 40_000, interactive),
                new ToolCallBudgetGuard(30, 50, interactive),
                new RepetitiveToolGuard(3, interactive));
    }

    /**
     * Relaxed governance pack: looser limits for long-running interactive sessions.
     *
     * <ul>
     *   <li>MaxTurns: warn=50, force=80
     *   <li>ContextSize: warn=80k, critical=120k tokens
     *   <li>ToolBudget: warn=150, force=250 calls
     *   <li>RepetitiveTool: threshold=6 consecutive
     * </ul>
     */
    public static List<Object> relaxed() {
        return relaxed(false);
    }

    public static List<Object> relaxed(boolean interactive) {
        return List.of(
                new MaxTurnsGuard(50, 80, interactive),
                new ContextSizeGuard(80_000, 120_000, interactive),
                new ToolCallBudgetGuard(150, 250, interactive),
                new RepetitiveToolGuard(6, interactive));
    }
}
