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
package io.kairo.core.context.recovery;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileAccessTrackerTest {

    @Test
    @DisplayName("Record and retrieve files in most-recent-first order")
    void testRecordAndRetrieve() {
        FileAccessTracker tracker = new FileAccessTracker();

        tracker.recordAccess("/path/to/A.java");
        tracker.recordAccess("/path/to/B.java");
        tracker.recordAccess("/path/to/C.java");

        List<String> files = tracker.getRecentFiles();
        assertEquals(3, files.size());
        assertEquals("/path/to/C.java", files.get(0));
        assertEquals("/path/to/B.java", files.get(1));
        assertEquals("/path/to/A.java", files.get(2));
    }

    @Test
    @DisplayName("LRU eviction at 5 files: add 6, verify oldest dropped")
    void testLruEvictionAt5() {
        FileAccessTracker tracker = new FileAccessTracker();

        tracker.recordAccess("/path/file1.java");
        tracker.recordAccess("/path/file2.java");
        tracker.recordAccess("/path/file3.java");
        tracker.recordAccess("/path/file4.java");
        tracker.recordAccess("/path/file5.java");
        tracker.recordAccess("/path/file6.java"); // Should evict file1

        List<String> files = tracker.getRecentFiles();
        assertEquals(5, files.size());
        assertFalse(files.contains("/path/file1.java")); // evicted
        assertEquals("/path/file6.java", files.get(0)); // most recent
        assertEquals("/path/file2.java", files.get(4)); // oldest remaining
    }

    @Test
    @DisplayName("Duplicate access moves file to front")
    void testDuplicateAccessMovesToFront() {
        FileAccessTracker tracker = new FileAccessTracker();

        tracker.recordAccess("/path/A.java");
        tracker.recordAccess("/path/B.java");
        tracker.recordAccess("/path/C.java");

        // Re-access A — should move to front
        tracker.recordAccess("/path/A.java");

        List<String> files = tracker.getRecentFiles();
        assertEquals(3, files.size());
        assertEquals("/path/A.java", files.get(0));
        assertEquals("/path/C.java", files.get(1));
        assertEquals("/path/B.java", files.get(2));
    }

    @Test
    @DisplayName("clear() removes all tracked files")
    void testClear() {
        FileAccessTracker tracker = new FileAccessTracker();

        tracker.recordAccess("/path/A.java");
        tracker.recordAccess("/path/B.java");
        assertFalse(tracker.getRecentFiles().isEmpty());

        tracker.clear();
        assertTrue(tracker.getRecentFiles().isEmpty());
    }

    @Test
    @DisplayName("Thread safety: concurrent access does not corrupt state")
    void testThreadSafety() throws InterruptedException {
        FileAccessTracker tracker = new FileAccessTracker();
        int threadCount = 10;
        int opsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(
                    () -> {
                        try {
                            for (int i = 0; i < opsPerThread; i++) {
                                tracker.recordAccess(
                                        "/path/thread-" + threadId + "-file-" + i + ".java");
                                tracker.getRecentFiles(); // concurrent read
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads should complete within timeout");
        executor.shutdown();

        // After all concurrent operations, should have exactly 5 files
        List<String> files = tracker.getRecentFiles();
        assertEquals(5, files.size());
    }

    @Test
    @DisplayName("Null and blank paths are ignored")
    void testNullAndBlankIgnored() {
        FileAccessTracker tracker = new FileAccessTracker();

        tracker.recordAccess(null);
        tracker.recordAccess("");
        tracker.recordAccess("   ");

        assertTrue(tracker.getRecentFiles().isEmpty());
    }

    @Test
    @DisplayName("getRecentFiles returns a defensive copy")
    void testDefensiveCopy() {
        FileAccessTracker tracker = new FileAccessTracker();
        tracker.recordAccess("/path/A.java");

        List<String> files = tracker.getRecentFiles();
        files.clear(); // Modify the returned list

        // Original tracker should not be affected
        assertEquals(1, tracker.getRecentFiles().size());
    }
}
