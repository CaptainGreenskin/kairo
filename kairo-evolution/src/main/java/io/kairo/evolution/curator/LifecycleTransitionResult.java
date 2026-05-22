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

import java.time.Instant;
import java.util.List;

/**
 * Summary of a single curator pass. Mirrors Hermes' {@code apply_automatic_transitions} return
 * shape but exposes the actual skill names so operators / UIs can render them.
 */
public record LifecycleTransitionResult(
        Instant runAt,
        int checked,
        List<String> markedStale,
        List<String> archived,
        List<String> reactivated,
        List<String> skippedImmune) {

    public int totalChanged() {
        return markedStale.size() + archived.size() + reactivated.size();
    }
}
