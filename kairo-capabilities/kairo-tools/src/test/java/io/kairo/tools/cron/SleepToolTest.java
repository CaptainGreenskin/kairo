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
package io.kairo.tools.cron;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SleepToolTest {

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    private final SleepTool tool = new SleepTool();

    @Test
    void missingDuration_returnsError() {
        ToolResult r = tool.execute(Map.of(), CTX).block();
        assertThat(r.isError()).isTrue();
        assertThat(r.content()).contains("'duration_seconds' is required");
    }

    @Test
    void zeroDuration_returnsError() {
        ToolResult r = tool.execute(Map.of("duration_seconds", 0), CTX).block();
        assertThat(r.isError()).isTrue();
        assertThat(r.content()).contains("> 0");
    }

    @Test
    void negativeDuration_returnsError() {
        ToolResult r = tool.execute(Map.of("duration_seconds", -1), CTX).block();
        assertThat(r.isError()).isTrue();
        assertThat(r.content()).contains("> 0");
    }

    @Test
    void overCapDuration_returnsErrorPointingAtCron() {
        ToolResult r =
                tool.execute(Map.of("duration_seconds", SleepTool.MAX_DURATION_SECONDS + 1), CTX)
                        .block();
        assertThat(r.isError()).isTrue();
        assertThat(r.content()).contains("24h cap").contains("CronCreate");
    }

    @Test
    void atCap_passesBoundaryCheck_andStartsSleeping() {
        // The cap is INCLUSIVE — duration == MAX must NOT error. Don't actually wait 24h;
        // start the publisher with a tiny timeout and verify we get TimeoutException (proving
        // we entered the delay path) rather than a tool-level error result.
        Mono<ToolResult> publisher =
                tool.execute(Map.of("duration_seconds", SleepTool.MAX_DURATION_SECONDS), CTX)
                        .timeout(Duration.ofMillis(50));
        try {
            publisher.block();
            throw new AssertionError("Expected TimeoutException — sleep should have started");
        } catch (RuntimeException expected) {
            // TimeoutException wrapped in the Reactor exception chain — both are fine; the point
            // is that we entered the delay path rather than short-circuiting with an error result.
        }
    }

    @Test
    void shortSleep_completesAndReportsDuration() {
        // 1-second real wait. Could use virtual time but for short durations the real path is the
        // most faithful integration check — confirms Mono.delay() emits a non-error result and
        // the success builder constructs the expected metadata shape.
        long start = System.currentTimeMillis();
        ToolResult r = tool.execute(Map.of("duration_seconds", 1), CTX).block();
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(r.isError()).isFalse();
        assertThat(r.content()).startsWith("Slept for");
        assertThat(r.metadata().get("interrupted")).isEqualTo(false);
        assertThat(r.metadata().get("slept_seconds")).isInstanceOf(Long.class);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(900); // tolerate jitter
    }

    @Test
    void durationAsString_isAccepted() {
        // Some JSON parsers stringify small integers. Don't reject just because the type is wrong.
        ToolResult r = tool.execute(Map.of("duration_seconds", "1"), CTX).block();
        assertThat(r.isError()).isFalse();
    }

    @Test
    void durationAsUnparseableString_returnsError() {
        ToolResult r = tool.execute(Map.of("duration_seconds", "not-a-number"), CTX).block();
        assertThat(r.isError()).isTrue();
        assertThat(r.content()).contains("required");
    }

    @Test
    void cancellationProducesNoSilentBlock() {
        // The Mono is cancellable — a downstream Mono.timeout() can cut it off and the publisher
        // must not deadlock. We verify by racing a 100ms timeout against a 60s sleep; if the
        // publisher didn't honor cancellation, the test would hang past the JUnit timeout.
        Mono<ToolResult> publisher =
                tool.execute(Map.of("duration_seconds", 60), CTX).timeout(Duration.ofMillis(100));

        long start = System.currentTimeMillis();
        try {
            publisher.block(Duration.ofSeconds(5));
            throw new AssertionError("Expected TimeoutException");
        } catch (RuntimeException expected) {
            long elapsedMs = System.currentTimeMillis() - start;
            assertThat(elapsedMs).isLessThan(2_000); // must have woken well before the 60s sleep
        }
    }
}
