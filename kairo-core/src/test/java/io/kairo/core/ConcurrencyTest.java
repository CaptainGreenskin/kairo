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
package io.kairo.core;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.context.DefaultContextManager;
import io.kairo.core.memory.FileMemoryStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Concurrency safety tests for thread-safe classes in kairo-core.
 *
 * <p>Verifies that {@link FileMemoryStore} (ReadWriteLock-based) and {@link
 * DefaultContextManager} (CopyOnWriteArrayList-based) behave correctly under concurrent access.
 */
class ConcurrencyTest {

    private static final int THREAD_COUNT = 20;

    @TempDir Path tempDir;

    // ── FileMemoryStore ──────────────────────────────────────────────────────

    @Test
    @DisplayName("FileMemoryStore handles concurrent writes without data corruption")
    @Timeout(10)
    void fileMemoryStoreHandlesConcurrentWrites() throws Exception {
        FileMemoryStore store = new FileMemoryStore(tempDir);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        MemoryEntry entry = new MemoryEntry(
                                "entry-" + idx,
                                "content-" + idx,
                                MemoryScope.SESSION,
                                Instant.now(),
                                List.of("tag-" + idx),
                                true);
                        store.save(entry).block();
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Release all threads simultaneously
            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            assertTrue(errors.isEmpty(), "Errors during concurrent writes: " + errors);

            // Verify all entries were saved correctly
            List<MemoryEntry> saved =
                    store.list(MemoryScope.SESSION).collectList().block();
            assertNotNull(saved);
            assertEquals(THREAD_COUNT, saved.size(), "Expected all entries to be saved");

            // Verify each entry is individually readable and has correct content
            Set<String> ids = ConcurrentHashMap.newKeySet();
            for (int i = 0; i < THREAD_COUNT; i++) {
                MemoryEntry e = store.get("entry-" + i).block();
                assertNotNull(e, "Entry entry-" + i + " should exist");
                assertEquals("content-" + i, e.content());
                ids.add(e.id());
            }
            assertEquals(THREAD_COUNT, ids.size(), "All entry IDs should be unique");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("FileMemoryStore handles concurrent reads and writes without exceptions")
    @Timeout(10)
    void fileMemoryStoreHandlesConcurrentReadsAndWrites() throws Exception {
        FileMemoryStore store = new FileMemoryStore(tempDir);

        // Pre-populate some data so reads have something to find
        for (int i = 0; i < 5; i++) {
            store.save(new MemoryEntry(
                            "seed-" + i,
                            "seed-content-" + i,
                            MemoryScope.SESSION,
                            Instant.now(),
                            List.of(),
                            true))
                    .block();
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successfulReads = new AtomicInteger(0);
        AtomicInteger successfulWrites = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (idx % 2 == 0) {
                            // Writer thread
                            MemoryEntry entry = new MemoryEntry(
                                    "write-" + idx,
                                    "write-content-" + idx,
                                    MemoryScope.SESSION,
                                    Instant.now(),
                                    List.of(),
                                    true);
                            store.save(entry).block();
                            successfulWrites.incrementAndGet();
                        } else {
                            // Reader thread — list and get
                            List<MemoryEntry> entries =
                                    store.list(MemoryScope.SESSION)
                                            .collectList()
                                            .block();
                            assertNotNull(entries);
                            // Also try individual get
                            store.get("seed-0").block();
                            successfulReads.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            assertTrue(errors.isEmpty(), "Errors during concurrent reads/writes: " + errors);

            // Verify reads and writes both succeeded
            assertTrue(successfulReads.get() > 0, "At least one read should have succeeded");
            assertTrue(successfulWrites.get() > 0, "At least one write should have succeeded");

            // Verify all written entries are present
            List<MemoryEntry> all =
                    store.list(MemoryScope.SESSION).collectList().block();
            assertNotNull(all);
            // seed entries (5) + writer threads (even indices: 0,2,4,6,8,10,12,14,16,18 = 10)
            assertEquals(5 + successfulWrites.get(), all.size());
        } finally {
            executor.shutdownNow();
        }
    }

    // ── DefaultContextManager ────────────────────────────────────────────────

    @Test
    @DisplayName("DefaultContextManager handles concurrent message additions")
    @Timeout(10)
    void contextManagerHandlesConcurrentMessageAdditions() throws Exception {
        DefaultContextManager manager = new DefaultContextManager();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Msg msg = Msg.builder()
                                .id("msg-" + idx)
                                .role(MsgRole.USER)
                                .addContent(new Content.TextContent("message-" + idx))
                                .tokenCount(10)
                                .build();
                        manager.addMessage(msg);
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            assertTrue(errors.isEmpty(), "Errors during concurrent additions: " + errors);

            // Verify all messages are present (no lost updates)
            List<Msg> messages = manager.getMessages();
            assertEquals(THREAD_COUNT, messages.size(),
                    "All messages should be present — no lost updates");

            // Verify each message ID is present
            Set<String> ids = ConcurrentHashMap.newKeySet();
            for (Msg m : messages) {
                ids.add(m.id());
            }
            assertEquals(THREAD_COUNT, ids.size(), "All message IDs should be unique");

            // Verify token count reflects all additions
            assertEquals(THREAD_COUNT * 10, manager.getTokenCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("DefaultContextManager concurrent reads during writes cause no ConcurrentModificationException")
    @Timeout(10)
    void contextManagerHandlesConcurrentReadsDuringWrites() throws Exception {
        DefaultContextManager manager = new DefaultContextManager();

        // Pre-populate some messages
        for (int i = 0; i < 10; i++) {
            manager.addMessage(Msg.builder()
                    .id("seed-" + i)
                    .role(MsgRole.USER)
                    .addContent(new Content.TextContent("seed-" + i))
                    .tokenCount(5)
                    .build());
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successfulReads = new AtomicInteger(0);
        AtomicInteger successfulWrites = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        try {
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        if (idx % 2 == 0) {
                            // Writer thread
                            Msg msg = Msg.builder()
                                    .id("write-" + idx)
                                    .role(MsgRole.ASSISTANT)
                                    .addContent(new Content.TextContent("response-" + idx))
                                    .tokenCount(5)
                                    .build();
                            manager.addMessage(msg);
                            successfulWrites.incrementAndGet();
                        } else {
                            // Reader thread — iterate all messages
                            List<Msg> messages = manager.getMessages();
                            // Force iteration — must not throw ConcurrentModificationException
                            int count = 0;
                            for (Msg m : messages) {
                                assertNotNull(m.id());
                                assertNotNull(m.role());
                                count++;
                            }
                            assertTrue(count >= 10, "Should see at least seed messages");
                            successfulReads.incrementAndGet();
                        }
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
            assertTrue(errors.isEmpty(),
                    "Errors during concurrent reads/writes (ConcurrentModificationException?): "
                            + errors);

            assertTrue(successfulReads.get() > 0, "At least one read should have succeeded");
            assertTrue(successfulWrites.get() > 0, "At least one write should have succeeded");

            // Final state: seed (10) + writer threads (even indices = 10)
            List<Msg> finalMessages = manager.getMessages();
            assertEquals(10 + successfulWrites.get(), finalMessages.size());
        } finally {
            executor.shutdownNow();
        }
    }
}
