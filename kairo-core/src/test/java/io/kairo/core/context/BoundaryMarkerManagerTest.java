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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoundaryMarkerManagerTest {

    private BoundaryMarkerManager manager;

    @BeforeEach
    void setUp() {
        manager = new BoundaryMarkerManager();
    }

    private static BoundaryMarker marker(String strategy, int orig, int compacted, int saved) {
        return new BoundaryMarker(Instant.now(), strategy, orig, compacted, saved);
    }

    @Test
    void constructorDoesNotThrow() {
        assertThat(new BoundaryMarkerManager()).isNotNull();
    }

    @Test
    void initialStateIsEmpty() {
        assertThat(manager.getMarkers()).isEmpty();
        assertThat(manager.compactionCount()).isZero();
        assertThat(manager.totalTokensSaved()).isZero();
    }

    @Test
    void recordAddsMarker() {
        manager.record(marker("snip", 100, 50, 200));
        assertThat(manager.compactionCount()).isEqualTo(1);
        assertThat(manager.getMarkers()).hasSize(1);
    }

    @Test
    void recordedMarkerPreservesFields() {
        BoundaryMarker m = marker("micro", 80, 40, 150);
        manager.record(m);
        BoundaryMarker stored = manager.getMarkers().get(0);
        assertThat(stored.strategyName()).isEqualTo("micro");
        assertThat(stored.originalMessageCount()).isEqualTo(80);
        assertThat(stored.compactedMessageCount()).isEqualTo(40);
        assertThat(stored.tokensSaved()).isEqualTo(150);
    }

    @Test
    void totalTokensSavedSumsAllMarkers() {
        manager.record(marker("snip", 100, 50, 100));
        manager.record(marker("micro", 50, 20, 200));
        assertThat(manager.totalTokensSaved()).isEqualTo(300);
    }

    @Test
    void compactionCountIncreasesWithEachRecord() {
        manager.record(marker("a", 10, 5, 10));
        manager.record(marker("b", 5, 2, 20));
        manager.record(marker("c", 2, 1, 30));
        assertThat(manager.compactionCount()).isEqualTo(3);
    }

    @Test
    void getMarkersReturnsUnmodifiableView() {
        manager.record(marker("snip", 10, 5, 50));
        var list = manager.getMarkers();
        assertThat(list).hasSize(1);
        // Returned list should not allow mutation
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> list.add(marker("x", 1, 1, 0)));
    }

    @Test
    void clearResetsAllState() {
        manager.record(marker("snip", 100, 50, 300));
        manager.record(marker("micro", 50, 25, 200));
        manager.clear();
        assertThat(manager.getMarkers()).isEmpty();
        assertThat(manager.compactionCount()).isZero();
        assertThat(manager.totalTokensSaved()).isZero();
    }

    @Test
    void markersOrderPreserved() {
        manager.record(marker("first", 10, 5, 10));
        manager.record(marker("second", 5, 2, 20));
        var markers = manager.getMarkers();
        assertThat(markers.get(0).strategyName()).isEqualTo("first");
        assertThat(markers.get(1).strategyName()).isEqualTo("second");
    }
}
