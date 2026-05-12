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
package io.kairo.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CacheStatsTest {

    @Test
    void initialState() {
        var stats = new CacheStats();
        assertThat(stats.hits()).isZero();
        assertThat(stats.misses()).isZero();
        assertThat(stats.evictions()).isZero();
        assertThat(stats.bytesSaved()).isZero();
        assertThat(stats.hitRatio()).isZero();
    }

    @Test
    void singleHit() {
        var stats = new CacheStats();
        stats.recordHit(100);
        assertThat(stats.hits()).isEqualTo(1);
        assertThat(stats.misses()).isZero();
        assertThat(stats.hitRatio()).isEqualTo(1.0);
        assertThat(stats.bytesSaved()).isEqualTo(100);
    }

    @Test
    void hitAndMiss() {
        var stats = new CacheStats();
        stats.recordHit(200);
        stats.recordMiss();
        assertThat(stats.hits()).isEqualTo(1);
        assertThat(stats.misses()).isEqualTo(1);
        assertThat(stats.hitRatio()).isEqualTo(0.5);
        assertThat(stats.bytesSaved()).isEqualTo(200);
    }

    @Test
    void bytesSavedAccumulates() {
        var stats = new CacheStats();
        stats.recordHit(100);
        stats.recordHit(200);
        stats.recordHit(50);
        assertThat(stats.bytesSaved()).isEqualTo(350);
        assertThat(stats.hits()).isEqualTo(3);
    }

    @Test
    void evictionTracking() {
        var stats = new CacheStats();
        stats.recordEviction();
        stats.recordEviction();
        assertThat(stats.evictions()).isEqualTo(2);
    }

    @Test
    void hitRatioWithZeroHit() {
        var stats = new CacheStats();
        stats.recordMiss();
        stats.recordMiss();
        assertThat(stats.hitRatio()).isZero();
    }

    @Test
    void hitRatioWithManyHits() {
        var stats = new CacheStats();
        for (int i = 0; i < 90; i++) {
            stats.recordHit(10);
        }
        for (int i = 0; i < 10; i++) {
            stats.recordMiss();
        }
        assertThat(stats.hitRatio()).isEqualTo(0.9);
    }

    @Test
    void concurrentRecordHitAndRecordMiss() throws InterruptedException {
        var stats = new CacheStats();
        int threadCount = 10;
        int opsPerThread = 1000;
        var latch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            for (int t = 0; t < threadCount; t++) {
                executor.submit(
                        () -> {
                            for (int i = 0; i < opsPerThread; i++) {
                                if (i % 2 == 0) {
                                    stats.recordHit(10);
                                } else {
                                    stats.recordMiss();
                                }
                            }
                            latch.countDown();
                        });
            }
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
        }

        assertThat(stats.hits()).isEqualTo(threadCount * opsPerThread / 2);
        assertThat(stats.misses()).isEqualTo(threadCount * opsPerThread / 2);
    }

    @Test
    void toStringContainsAllFields() {
        var stats = new CacheStats();
        stats.recordHit(500);
        stats.recordMiss();
        stats.recordEviction();
        String s = stats.toString();
        assertThat(s).contains("hits=1");
        assertThat(s).contains("misses=1");
        assertThat(s).contains("evictions=1");
        assertThat(s).contains("bytesSaved=500");
        assertThat(s).contains("ratio=50.0%");
    }
}

class CachingToolExecutorStatsTest {

    private static ToolResult ok(String content) {
        return ToolResult.success("id", content);
    }

    private static ToolExecutor countingDelegate(
            java.util.concurrent.atomic.AtomicInteger counter, ToolResult result) {
        return new ToolExecutor() {
            @Override
            public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
                counter.incrementAndGet();
                return Mono.just(result);
            }

            @Override
            public Mono<ToolResult> execute(
                    String toolName, Map<String, Object> input, Duration timeout) {
                counter.incrementAndGet();
                return Mono.just(result);
            }

            @Override
            public Flux<ToolResult> executeParallel(List<ToolInvocation> invocations) {
                return Flux.empty();
            }
        };
    }

    @Test
    void cacheHitIncrementsStats() {
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        var caching =
                new CachingToolExecutor(
                        countingDelegate(counter, ok("cached-content")),
                        Duration.ofSeconds(60),
                        100,
                        Set.of());

        caching.execute("read", Map.of("path", "/tmp/x")).block();
        caching.execute("read", Map.of("path", "/tmp/x")).block();

        var stats = caching.getCacheStats("read");
        assertThat(stats.hits()).isEqualTo(1);
        assertThat(stats.misses()).isEqualTo(1);
        assertThat(stats.hitRatio()).isEqualTo(0.5);
        assertThat(stats.bytesSaved()).isEqualTo("cached-content".length());
    }

    @Test
    void cacheMissIncrementsStats() {
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        var caching =
                new CachingToolExecutor(
                        countingDelegate(counter, ok("result")),
                        Duration.ofSeconds(60),
                        100,
                        Set.of());

        caching.execute("read", Map.of("path", "/a")).block();
        caching.execute("read", Map.of("path", "/b")).block();

        var stats = caching.getCacheStats("read");
        assertThat(stats.misses()).isEqualTo(2);
        assertThat(stats.hits()).isZero();
    }

    @Test
    void getCacheStatsReturnsPerToolSnapshot() {
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        var caching =
                new CachingToolExecutor(
                        countingDelegate(counter, ok("v")), Duration.ofSeconds(60), 100, Set.of());

        caching.execute("read", Map.of("path", "/a")).block();
        caching.execute("bash", Map.of("cmd", "ls")).block();
        caching.execute("read", Map.of("path", "/a")).block();

        var allStats = caching.getCacheStats();
        assertThat(allStats).containsOnlyKeys("read", "bash");
        assertThat(allStats.get("read").hits()).isEqualTo(1);
        assertThat(allStats.get("read").misses()).isEqualTo(1);
        assertThat(allStats.get("bash").misses()).isEqualTo(1);
    }

    @Test
    void getCacheStatsForUnknownToolReturnsEmptyStats() {
        var caching = new CachingToolExecutor(new NoopExecutor());
        var stats = caching.getCacheStats("unknown");
        assertThat(stats.hits()).isZero();
        assertThat(stats.misses()).isZero();
        assertThat(stats.hitRatio()).isZero();
    }

    private static class NoopExecutor implements ToolExecutor {
        @Override
        public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
            return Mono.just(ok("noop"));
        }

        @Override
        public Mono<ToolResult> execute(
                String toolName, Map<String, Object> input, Duration timeout) {
            return Mono.just(ok("noop"));
        }

        @Override
        public Flux<ToolResult> executeParallel(List<ToolInvocation> invocations) {
            return Flux.empty();
        }
    }
}
