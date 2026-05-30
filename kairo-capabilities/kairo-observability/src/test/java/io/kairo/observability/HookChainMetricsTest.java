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
package io.kairo.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.kairo.core.health.HookChainObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HookChainMetricsTest {

    private MeterRegistry registry;
    private HookChainMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new HookChainMetrics(registry);
    }

    @AfterEach
    void resetGlobal() {
        // install() touches the process-wide observer; keep tests order-independent.
        HookChainObserver.setGlobal(null);
    }

    @Test
    void onHookFired_incrementsCounterAndRecordsTimer() {
        metrics.onHookFired("PRE_REASONING", "CONTINUE", Duration.ofMillis(42));

        Counter c =
                registry.find(HookChainMetrics.FIRED_TOTAL)
                        .tag("phase", "PRE_REASONING")
                        .tag("decision", "CONTINUE")
                        .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);

        Timer t =
                registry.find(HookChainMetrics.DURATION)
                        .tag("phase", "PRE_REASONING")
                        .tag("outcome", "success")
                        .timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1);
        assertThat(t.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(42.0, within(2.0));
    }

    @Test
    void onHookFailed_incrementsFailureCounterTaggedInProcess() {
        metrics.onHookFailed("PRE_ACTING", new IllegalStateException("boom"), Duration.ofMillis(7));

        Counter c =
                registry.find(HookChainMetrics.FAILURES_TOTAL)
                        .tag("phase", "PRE_ACTING")
                        .tag("kind", "in_process")
                        .tag("errorType", "IllegalStateException")
                        .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);

        Timer t =
                registry.find(HookChainMetrics.DURATION)
                        .tag("phase", "PRE_ACTING")
                        .tag("outcome", "failure")
                        .timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1);
    }

    @Test
    void onExternalHookFailure_incrementsFailureCounterTaggedExternal() {
        metrics.onExternalHookFailure(
                "PRE_ACTING", "command:./guard.sh", new RuntimeException("exit 1"));

        Counter c =
                registry.find(HookChainMetrics.FAILURES_TOTAL)
                        .tag("phase", "PRE_ACTING")
                        .tag("kind", "external")
                        .tag("errorType", "RuntimeException")
                        .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);

        // External failures do not record duration — registry should have no duration meter at all.
        assertThat(registry.find(HookChainMetrics.DURATION).timers()).isEmpty();
    }

    @Test
    void multipleFirings_accumulatePerTagCombination() {
        metrics.onHookFired("PRE_REASONING", "CONTINUE", Duration.ofMillis(10));
        metrics.onHookFired("PRE_REASONING", "CONTINUE", Duration.ofMillis(20));
        metrics.onHookFired("PRE_REASONING", "ABORT", Duration.ofMillis(5));
        metrics.onHookFired("PRE_ACTING", "CONTINUE", Duration.ofMillis(3));

        assertThat(
                        registry.find(HookChainMetrics.FIRED_TOTAL)
                                .tag("phase", "PRE_REASONING")
                                .tag("decision", "CONTINUE")
                                .counter()
                                .count())
                .isEqualTo(2.0);
        assertThat(
                        registry.find(HookChainMetrics.FIRED_TOTAL)
                                .tag("phase", "PRE_REASONING")
                                .tag("decision", "ABORT")
                                .counter()
                                .count())
                .isEqualTo(1.0);
        assertThat(
                        registry.find(HookChainMetrics.FIRED_TOTAL)
                                .tag("phase", "PRE_ACTING")
                                .tag("decision", "CONTINUE")
                                .counter()
                                .count())
                .isEqualTo(1.0);
    }

    @Test
    void nullErrorType_isCapturedAsUnknown() {
        metrics.onExternalHookFailure("PRE_ACTING", "command:foo", null);

        Counter c =
                registry.find(HookChainMetrics.FAILURES_TOTAL)
                        .tag("phase", "PRE_ACTING")
                        .tag("kind", "external")
                        .tag("errorType", "unknown")
                        .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void install_registersAsGlobalObserver() {
        HookChainMetrics installed = HookChainMetrics.install(registry);

        assertThat(HookChainObserver.global()).isSameAs(installed);
    }
}
