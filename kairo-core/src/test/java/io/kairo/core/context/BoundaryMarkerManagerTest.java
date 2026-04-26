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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.context.BoundaryMarker;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BoundaryMarkerManager}. */
class BoundaryMarkerManagerTest {

    private BoundaryMarkerManager manager;

    @BeforeEach
    void setUp() {
        manager = new BoundaryMarkerManager();
    }

    private static BoundaryMarker marker(String strategy, int original, int compacted, int saved) {
        return new BoundaryMarker(Instant.now(), strategy, original, compacted, saved);
    }

    @Test
    void initialState_isEmpty() {
        assertThat(manager.getMarkers()).isEmpty();
        assertThat(manager.totalTokensSaved()).isEqualTo(0);
        assertThat(manager.compactionCount()).isEqualTo(0);
    }

    @Test
    void singleMarker_correctAggregation() {
        manager.record(marker("snip", 10, 7, 300));

        assertThat(manager.compactionCount()).isEqualTo(1);
        assertThat(manager.totalTokensSaved()).isEqualTo(300);
    }

    @Test
    void multipleMarkers_tokensSavedIsSummed() {
        manager.record(marker("snip", 10, 7, 300));
        manager.record(marker("micro", 7, 4, 500));
        manager.record(marker("collapse", 4, 2, 800));

        assertThat(manager.compactionCount()).isEqualTo(3);
        assertThat(manager.totalTokensSaved()).isEqualTo(1600);
    }

    @Test
    void clear_resetsAllState() {
        manager.record(marker("snip", 10, 7, 300));
        manager.record(marker("micro", 7, 4, 500));

        manager.clear();

        assertThat(manager.getMarkers()).isEmpty();
        assertThat(manager.compactionCount()).isEqualTo(0);
        assertThat(manager.totalTokensSaved()).isEqualTo(0);
    }

    @Test
    void getMarkers_returnsUnmodifiableView() {
        manager.record(marker("snip", 10, 7, 300));
        List<BoundaryMarker> view = manager.getMarkers();

        assertThatThrownBy(() -> view.add(marker("fake", 1, 1, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getMarkers_externalModificationDoesNotAffectInternalState() {
        manager.record(marker("snip", 10, 7, 300));
        List<BoundaryMarker> snapshot = manager.getMarkers();

        // Record another marker AFTER taking the snapshot
        manager.record(marker("micro", 7, 4, 500));

        // Snapshot should still have 1 entry (it's a copy)
        assertThat(snapshot).hasSize(1);
        assertThat(manager.compactionCount()).isEqualTo(2);
    }

    @Test
    void markerFields_arePreserved() {
        Instant ts = Instant.parse("2026-04-26T10:00:00Z");
        BoundaryMarker m = new BoundaryMarker(ts, "collapse", 20, 5, 12000);
        manager.record(m);

        BoundaryMarker stored = manager.getMarkers().get(0);
        assertThat(stored.strategyName()).isEqualTo("collapse");
        assertThat(stored.originalMessageCount()).isEqualTo(20);
        assertThat(stored.compactedMessageCount()).isEqualTo(5);
        assertThat(stored.tokensSaved()).isEqualTo(12000);
        assertThat(stored.timestamp()).isEqualTo(ts);
    }
}
