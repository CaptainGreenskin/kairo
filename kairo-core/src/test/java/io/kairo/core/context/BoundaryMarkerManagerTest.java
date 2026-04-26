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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.context.BoundaryMarker;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BoundaryMarkerManagerTest {

    private static BoundaryMarker marker(String strategy, int original, int compacted, int saved) {
        return new BoundaryMarker(Instant.now(), strategy, original, compacted, saved);
    }

    @Test
    void initiallyEmpty() {
        BoundaryMarkerManager manager = new BoundaryMarkerManager();
        assertThat(manager.getMarkers()).isEmpty();
    }

    @Test
    void compactionCountZeroInitially() {
        assertThat(new BoundaryMarkerManager().compactionCount()).isZero();
    }

    @Test
    void totalTokensSavedZeroInitially() {
        assertThat(new BoundaryMarkerManager().totalTokensSaved()).isZero();
    }

    @Test
    void recordAddsMarker() {
        BoundaryMarkerManager manager = new BoundaryMarkerManager();
        manager.record(marker("snip", 10, 8, 200));
        assertThat(manager.getMarkers()).hasSize(1);
    }

    @Test
    void compactionCountIncreasesAfterRecord() {
        BoundaryMarkerManager manager = new BoundaryMarkerManager();
        manager.record(marker("snip", 10, 8, 200));
        manager.record(marker("micro", 8, 5, 300));
        assertThat(manager.compactionCount()).isEqualTo(2);
    }

    @Test
    void totalTokensSavedSumsAllMarkers() {
        BoundaryMarkerManager manager = new BoundaryMarkerManager();
        manager.record(marker("snip", 10, 8, 200));
        manager.record(marker("micro", 8, 5, 300));
        assertThat(manager.totalTokensSaved()).isEqualTo(500);
    }

    @Test
    void getMarkersIsUnmodifiable() {
        BoundaryMarkerManager manager = new BoundaryMarkerManager();
        manager.record(marker("snip", 10, 8, 100));
        var list = manager.getMarkers();
        try {
            list.clear();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException ignored) {
        }
    }

    @Test
    void clearResetsAll() {
        BoundaryMarkerManager manager = new BoundaryMarkerManager();
        manager.record(marker("snip", 10, 8, 200));
        manager.clear();
        assertThat(manager.compactionCount()).isZero();
        assertThat(manager.totalTokensSaved()).isZero();
    }

    @Test
    void markerStrategyNamePreserved() {
        BoundaryMarkerManager manager = new BoundaryMarkerManager();
        manager.record(marker("collapse", 5, 3, 50));
        assertThat(manager.getMarkers().get(0).strategyName()).isEqualTo("collapse");
    }
}
