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
package io.kairo.core.context;

import io.kairo.api.context.BoundaryMarker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages boundary markers inserted after compaction operations.
 *
 * <p>Each compaction leaves a marker recording:
 *
 * <ul>
 *   <li>When the compaction occurred
 *   <li>Which strategy was used
 *   <li>How many messages were before/after
 *   <li>How many tokens were saved
 * </ul>
 *
 * <p>These markers serve as an audit trail and can be used for recovery or debugging compaction
 * behavior.
 */
public class BoundaryMarkerManager {

    private static final Logger log = LoggerFactory.getLogger(BoundaryMarkerManager.class);

    private final List<BoundaryMarker> markers = new CopyOnWriteArrayList<>();

    /**
     * Record a new boundary marker after a compaction operation.
     *
     * @param marker the boundary marker to record
     */
    public void record(BoundaryMarker marker) {
        markers.add(marker);
        log.info(
                "Boundary marker recorded: strategy={}, messages {} -> {}, tokens saved={}",
                marker.strategyName(),
                marker.originalMessageCount(),
                marker.compactedMessageCount(),
                marker.tokensSaved());
    }

    /**
     * Get all recorded boundary markers, ordered by timestamp.
     *
     * @return an unmodifiable list of markers
     */
    public List<BoundaryMarker> getMarkers() {
        return Collections.unmodifiableList(new ArrayList<>(markers));
    }

    /**
     * Get the total number of tokens saved across all compactions.
     *
     * @return total tokens saved
     */
    public int totalTokensSaved() {
        return markers.stream().mapToInt(BoundaryMarker::tokensSaved).sum();
    }

    /**
     * Get the total number of compaction operations performed.
     *
     * @return the compaction count
     */
    public int compactionCount() {
        return markers.size();
    }

    /** Clear all recorded markers. */
    public void clear() {
        markers.clear();
    }
}
