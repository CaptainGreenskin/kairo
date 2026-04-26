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
package io.kairo.api.context;

import io.kairo.api.Stable;
import java.time.Instant;

/**
 * Marker recording the boundary of a compaction operation.
 *
 * @param timestamp when the compaction occurred
 * @param strategyName the strategy that performed the compaction
 * @param originalMessageCount number of messages before compaction
 * @param compactedMessageCount number of messages after compaction
 * @param tokensSaved tokens reclaimed by the compaction
 */
@Stable(value = "Compaction boundary marker record; shape frozen since v0.1", since = "1.0.0")
public record BoundaryMarker(
        Instant timestamp,
        String strategyName,
        int originalMessageCount,
        int compactedMessageCount,
        int tokensSaved) {}
