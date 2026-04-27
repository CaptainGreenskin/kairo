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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CachingToolExecutorTest {

    private static ToolResult ok(String content) {
        return new ToolResult("id", content, false, Map.of());
    }

    private static ToolResult error(String content) {
        return new ToolResult("id", content, true, Map.of());
    }

    /** Counting delegate that returns a fixed result. */
    private static ToolExecutor countingDelegate(AtomicInteger counter, ToolResult result) {
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
    void firstCallDelegatesToUnderlying() {
        var counter = new AtomicInteger();
        var caching = new CachingToolExecutor(countingDelegate(counter, ok("result")));
        StepVerifier.create(caching.execute("read", Map.of("path", "/tmp/x")))
                .expectNextMatches(r -> r.content().equals("result"))
                .verifyComplete();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void secondCallReturnsCachedResult() {
        var counter = new AtomicInteger();
        var caching = new CachingToolExecutor(countingDelegate(counter, ok("cached")));

        caching.execute("read", Map.of("path", "/tmp/x")).block();
        StepVerifier.create(caching.execute("read", Map.of("path", "/tmp/x")))
                .expectNextMatches(r -> r.content().equals("cached"))
                .verifyComplete();

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void differentParamsDoNotShareCache() {
        var counter = new AtomicInteger();
        var caching = new CachingToolExecutor(countingDelegate(counter, ok("v")));

        caching.execute("read", Map.of("path", "/a")).block();
        caching.execute("read", Map.of("path", "/b")).block();

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void errorResultIsNotCached() {
        var counter = new AtomicInteger();
        var caching = new CachingToolExecutor(countingDelegate(counter, error("boom")));

        caching.execute("read", Map.of()).block();
        caching.execute("read", Map.of()).block();

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void ttlExpiryTriggersRefetch() throws InterruptedException {
        var counter = new AtomicInteger();
        var caching =
                new CachingToolExecutor(
                        countingDelegate(counter, ok("v")), Duration.ofMillis(30), 100, Set.of());

        caching.execute("read", Map.of()).block();
        Thread.sleep(50);
        caching.execute("read", Map.of()).block();

        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void invalidateRemovesOnlyTargetTool() {
        var counter = new AtomicInteger();
        var caching = new CachingToolExecutor(countingDelegate(counter, ok("v")));

        caching.execute("readA", Map.of("k", "v")).block();
        caching.execute("readB", Map.of("k", "v")).block();
        caching.invalidate("readA");

        caching.execute("readA", Map.of("k", "v")).block();
        caching.execute("readB", Map.of("k", "v")).block();

        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    void clearEmptiesAllEntries() {
        var counter = new AtomicInteger();
        var caching = new CachingToolExecutor(countingDelegate(counter, ok("v")));

        caching.execute("read", Map.of("a", 1)).block();
        caching.execute("read", Map.of("b", 2)).block();
        caching.clear();

        assertThat(caching.size()).isEqualTo(0);
        caching.execute("read", Map.of("a", 1)).block();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    void cachedToolsFilterLimitsScope() {
        var counter = new AtomicInteger();
        var caching =
                new CachingToolExecutor(
                        countingDelegate(counter, ok("v")),
                        Duration.ofSeconds(60),
                        100,
                        Set.of("read"));

        caching.execute("write", Map.of("k", "v")).block();
        caching.execute("write", Map.of("k", "v")).block();
        caching.execute("read", Map.of("k", "v")).block();
        caching.execute("read", Map.of("k", "v")).block();

        assertThat(counter.get()).isEqualTo(3);
    }
}
