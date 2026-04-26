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
import org.junit.jupiter.api.Test;

class BoundaryMarkerManagerTest {

    private BoundaryMarkerManager manager;

    private static BoundaryMarker marker(String strategy, int original, int compacted, int saved) {
        return new BoundaryMarker(Instant.now(), strategy, original, compacted, saved);
    }

    @BeforeEach
    void setUp() {
        manager = new BoundaryMarkerManager();
    }

    @Test
    void initiallyEmpty() {
        assertTrue(manager.getMarkers().isEmpty());
    }

    @Test
    void compactionCountZeroInitially() {
        assertEquals(0, manager.compactionCount());
    }

    @Test
    void totalTokensSavedZeroInitially() {
        assertEquals(0, manager.totalTokensSaved());
    }

    @Test
    void recordIncreasesCompactionCount() {
        manager.record(marker("snip", 10, 5, 100));
        assertEquals(1, manager.compactionCount());
    }

    @Test
    void recordAppearsInGetMarkers() {
        BoundaryMarker m = marker("micro", 20, 10, 200);
        manager.record(m);
        List<BoundaryMarker> list = manager.getMarkers();
        assertEquals(1, list.size());
        assertEquals(m, list.get(0));
    }

    @Test
    void multipleRecordsAccumulate() {
        manager.record(marker("snip", 10, 5, 100));
        manager.record(marker("micro", 20, 8, 300));
        manager.record(marker("collapse", 30, 3, 500));
        assertEquals(3, manager.compactionCount());
    }

    @Test
    void totalTokensSavedSumsAllMarkers() {
        manager.record(marker("a", 10, 5, 100));
        manager.record(marker("b", 20, 8, 200));
        assertEquals(300, manager.totalTokensSaved());
    }

    @Test
    void getMarkersIsUnmodifiable() {
        manager.record(marker("snip", 10, 5, 50));
        List<BoundaryMarker> list = manager.getMarkers();
        assertThrows(UnsupportedOperationException.class, () -> list.add(marker("x", 1, 1, 0)));
    }

    @Test
    void clearResetsCompactionCount() {
        manager.record(marker("snip", 10, 5, 100));
        manager.clear();
        assertEquals(0, manager.compactionCount());
    }

    @Test
    void clearResetsMarkersList() {
        manager.record(marker("snip", 10, 5, 100));
        manager.clear();
        assertTrue(manager.getMarkers().isEmpty());
    }

    @Test
    void clearResetsTotalTokensSaved() {
        manager.record(marker("snip", 10, 5, 100));
        manager.clear();
        assertEquals(0, manager.totalTokensSaved());
    }
}
