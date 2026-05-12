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
package io.kairo.api.tool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ToolEvent sealed hierarchy")
class ToolEventTest {

    @Test
    @DisplayName("Chunk is a ToolEvent")
    void chunkIsToolEvent() {
        ToolEvent.Chunk chunk = new ToolEvent.Chunk("data line", ToolEvent.StreamKind.STDOUT);
        assertInstanceOf(ToolEvent.class, chunk);
        assertEquals("data line", chunk.data());
        assertEquals(ToolEvent.StreamKind.STDOUT, chunk.kind());
    }

    @Test
    @DisplayName("Progress is a ToolEvent")
    void progressIsToolEvent() {
        ToolEvent.Progress progress = new ToolEvent.Progress(0.5, "half done");
        assertInstanceOf(ToolEvent.class, progress);
        assertEquals(0.5, progress.pct(), 0.001);
        assertEquals("half done", progress.message());
    }

    @Test
    @DisplayName("Progress with indeterminate value")
    void progressIndeterminate() {
        ToolEvent.Progress progress = new ToolEvent.Progress(-1.0, "working...");
        assertEquals(-1.0, progress.pct(), 0.001);
    }

    @Test
    @DisplayName("NeedsApproval is a ToolEvent")
    void needsApprovalIsToolEvent() {
        ToolEvent.NeedsApproval approval =
                new ToolEvent.NeedsApproval("delete file /tmp/x", "destructive operation");
        assertInstanceOf(ToolEvent.class, approval);
        assertEquals("delete file /tmp/x", approval.description());
        assertEquals("destructive operation", approval.reason());
    }

    @Test
    @DisplayName("Final is a ToolEvent wrapping a ToolResult")
    void finalIsToolEvent() {
        ToolResult result = ToolResult.success("tu-1", "done");
        ToolEvent.Final fin = new ToolEvent.Final(result);
        assertInstanceOf(ToolEvent.class, fin);
        assertSame(result, fin.result());
    }

    @Test
    @DisplayName("Sealed interface permits exactly 4 variants")
    void sealedPermits() {
        assertTrue(ToolEvent.class.isSealed());
        Class<?>[] permitted = ToolEvent.class.getPermittedSubclasses();
        assertNotNull(permitted);
        assertEquals(4, permitted.length);
    }

    @Test
    @DisplayName("Pattern matching over ToolEvent works with instanceof")
    void patternMatchingWorks() {
        ToolEvent event1 = new ToolEvent.Chunk("err", ToolEvent.StreamKind.STDERR);
        ToolEvent event2 = new ToolEvent.Progress(1.0, "complete");
        ToolEvent event3 = new ToolEvent.NeedsApproval("action", "reason");
        ToolEvent event4 = new ToolEvent.Final(ToolResult.success("id", "ok"));

        assertTrue(event1 instanceof ToolEvent.Chunk);
        assertTrue(event2 instanceof ToolEvent.Progress);
        assertTrue(event3 instanceof ToolEvent.NeedsApproval);
        assertTrue(event4 instanceof ToolEvent.Final);

        // Verify mutual exclusivity
        assertFalse(event1 instanceof ToolEvent.Progress);
        assertFalse(event2 instanceof ToolEvent.Chunk);
    }

    @Test
    @DisplayName("StreamKind enum has exactly 2 values")
    void streamKindEnum() {
        ToolEvent.StreamKind[] values = ToolEvent.StreamKind.values();
        assertEquals(2, values.length);
        assertNotNull(ToolEvent.StreamKind.valueOf("STDOUT"));
        assertNotNull(ToolEvent.StreamKind.valueOf("STDERR"));
    }
}
