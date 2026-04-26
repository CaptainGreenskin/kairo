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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.BoundaryMarker;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoundaryMarkerManagerTest {

    private BoundaryMarkerManager manager;

    @BeforeEach
    void setUp() {
        manager = new BoundaryMarkerManager();
    }

    private BoundaryMarker marker(String strategy, int orig, int compacted, int saved) {
        return new BoundaryMarker(Instant.now(), strategy, orig, compacted, saved);
    }

    @Test
    @DisplayName("Initial state: empty markers, zero tokens saved, zero count")
    void initialState_isEmpty() {
        assertTrue(manager.getMarkers().isEmpty());
        assertEquals(0, manager.totalTokensSaved());
        assertEquals(0, manager.compactionCount());
    }

    @Test
    @DisplayName("record() adds a marker retrievable via getMarkers()")
    void record_addsMarker() {
        BoundaryMarker m = marker("snip", 10, 8, 200);
        manager.record(m);

        List<BoundaryMarker> markers = manager.getMarkers();
        assertEquals(1, markers.size());
        assertEquals("snip", markers.get(0).strategyName());
        assertEquals(10, markers.get(0).originalMessageCount());
        assertEquals(200, markers.get(0).tokensSaved());
    }

    @Test
    @DisplayName("Multiple records accumulate correctly")
    void record_multipleMarkers_accumulate() {
        manager.record(marker("snip", 10, 8, 200));
        manager.record(marker("micro", 8, 7, 150));
        manager.record(marker("auto", 7, 5, 500));

        assertEquals(3, manager.compactionCount());
        assertEquals(850, manager.totalTokensSaved());
    }

    @Test
    @DisplayName("getMarkers() returns unmodifiable view")
    void getMarkers_returnsUnmodifiableList() {
        manager.record(marker("snip", 5, 4, 100));
        List<BoundaryMarker> result = manager.getMarkers();
        assertThrows(UnsupportedOperationException.class, () -> result.add(marker("x", 1, 1, 0)));
    }

    @Test
    @DisplayName("clear() removes all markers and resets totals")
    void clear_resetsState() {
        manager.record(marker("snip", 10, 8, 300));
        manager.record(marker("micro", 8, 6, 200));
        assertEquals(2, manager.compactionCount());

        manager.clear();

        assertEquals(0, manager.compactionCount());
        assertEquals(0, manager.totalTokensSaved());
        assertTrue(manager.getMarkers().isEmpty());
    }

    @Test
    @DisplayName("totalTokensSaved() sums all marker tokensSaved values")
    void totalTokensSaved_sumsAll() {
        manager.record(marker("a", 10, 9, 100));
        manager.record(marker("b", 9, 8, 250));
        assertEquals(350, manager.totalTokensSaved());
    }

    @Test
    @DisplayName("record after clear works correctly")
    void recordAfterClear_works() {
        manager.record(marker("snip", 5, 4, 100));
        manager.clear();
        manager.record(marker("micro", 3, 2, 50));

        assertEquals(1, manager.compactionCount());
        assertEquals(50, manager.totalTokensSaved());
    }
}
