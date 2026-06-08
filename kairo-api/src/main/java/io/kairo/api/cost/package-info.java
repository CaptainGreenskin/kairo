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
 * Cost tracking SPI for monitoring token usage and estimated spend across model calls.
 *
 * <p>Core types:
 *
 * <ul>
 *   <li>{@link io.kairo.api.cost.CostTracker} — the SPI interface
 *   <li>{@link io.kairo.api.cost.UsageSummary} — immutable snapshot of cumulative usage
 *   <li>{@link io.kairo.api.cost.NoopCostTracker} — zero-overhead default
 * </ul>
 *
 * @since 1.5
 */
package io.kairo.api.cost;
