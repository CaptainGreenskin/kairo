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
package io.kairo.evolution.curator;

import java.time.Duration;

/**
 * Knobs for the {@link LifecycleCuratorDaemon} and {@code UmbrellaConsolidationPlanner}. Defaults
 * mirror Hermes: 7-day review interval, 2-hour idle gate, 30-day stale cutoff, 90-day archive
 * cutoff.
 */
public record CuratorConfig(
        Duration reviewInterval,
        Duration idleThreshold,
        Duration staleAfter,
        Duration archiveAfter,
        boolean enabled,
        boolean paused) {

    public static CuratorConfig defaults() {
        return new CuratorConfig(
                Duration.ofDays(7),
                Duration.ofHours(2),
                Duration.ofDays(30),
                Duration.ofDays(90),
                true,
                false);
    }

    public CuratorConfig withReviewInterval(Duration v) {
        return new CuratorConfig(v, idleThreshold, staleAfter, archiveAfter, enabled, paused);
    }

    public CuratorConfig withIdleThreshold(Duration v) {
        return new CuratorConfig(reviewInterval, v, staleAfter, archiveAfter, enabled, paused);
    }

    public CuratorConfig withStaleAfter(Duration v) {
        return new CuratorConfig(reviewInterval, idleThreshold, v, archiveAfter, enabled, paused);
    }

    public CuratorConfig withArchiveAfter(Duration v) {
        return new CuratorConfig(reviewInterval, idleThreshold, staleAfter, v, enabled, paused);
    }

    public CuratorConfig withEnabled(boolean v) {
        return new CuratorConfig(
                reviewInterval, idleThreshold, staleAfter, archiveAfter, v, paused);
    }

    public CuratorConfig withPaused(boolean v) {
        return new CuratorConfig(
                reviewInterval, idleThreshold, staleAfter, archiveAfter, enabled, v);
    }
}
