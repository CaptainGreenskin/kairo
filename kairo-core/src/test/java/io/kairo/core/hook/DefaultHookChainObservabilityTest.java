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
package io.kairo.core.hook;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.hook.HookHandler;
import io.kairo.api.hook.HookPhase;
import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.PreActingEvent;
import io.kairo.api.hook.PreReasoning;
import io.kairo.api.hook.PreReasoningEvent;
import io.kairo.api.tracing.NoopSpan;
import io.kairo.api.tracing.ObservationData;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.health.HookChainObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Locks in the observability contract for {@link DefaultHookChain}: each in-process firing emits
 * exactly one span, the span carries the expected {@code hook.*} metadata, failures flip the level
 * to ERROR, and the programmatic {@link DefaultHookChain.HookChainStats} snapshot reflects what
 * happened.
 *
 * <p>Mirrors {@code DefaultToolExecutorTest} and {@code TracingModelProviderTest} which use the
 * same RecordingTracer pattern — kept self-contained so it doesn't drift if the others change.
 */
class DefaultHookChainObservabilityTest {

    @AfterEach
    void resetGlobalObserver() {
        // The global observer is process-wide; reset to no-op so the suite stays order-independent.
        HookChainObserver.setGlobal(null);
    }

    static class GoodHandler {
        @PreReasoning
        public PreReasoningEvent onPre(PreReasoningEvent e) {
            return e;
        }
    }

    static class BoomHandler {
        @PreReasoning
        public PreReasoningEvent boom(PreReasoningEvent e) {
            throw new IllegalStateException("synthetic boom");
        }
    }

    static class AbortingHandler {
        @HookHandler(value = HookPhase.PRE_ACTING)
        public HookResult<PreActingEvent> abortIt(PreActingEvent e) {
            return HookResult.abort(e, "nope");
        }
    }

    @Test
    void firePhase_inProcess_emitsSpanAndIncrementsCounters() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultHookChain chain = new DefaultHookChain(tracer);
        chain.register(new GoodHandler());

        StepVerifier.create(chain.firePreReasoning(new PreReasoningEvent(List.of(), null, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.attributes)
                .containsEntry("langfuse.observation.type", "span")
                .containsEntry("hook.phase", "PRE_REASONING")
                .containsEntry("hook.decision", "CONTINUE")
                .containsEntry("hook.handler", GoodHandler.class.getName())
                .containsKey("hook.duration_ms")
                .containsEntry("langfuse.observation.level", "DEFAULT");
        assertThat(span.statusSuccess).isTrue();
        assertThat(span.ended).isTrue();

        DefaultHookChain.HookChainStats stats = chain.snapshot();
        assertThat(stats.firedByPhase()).containsEntry("PRE_REASONING", 1L);
        assertThat(stats.failuresByPhase()).isEmpty();
        assertThat(stats.decisionsByOutcome()).containsEntry("CONTINUE", 1L);
        assertThat(stats.externalHookFailures()).isZero();
    }

    @Test
    void firePhase_handlerThrows_recordsErrorSpanAndFailureCounter() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultHookChain chain = new DefaultHookChain(tracer);
        chain.register(new BoomHandler());

        StepVerifier.create(chain.firePreReasoning(new PreReasoningEvent(List.of(), null, false)))
                .expectErrorMessage("synthetic boom")
                .verify();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.attributes)
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("hook.phase", "PRE_REASONING")
                .containsEntry("hook.handler", BoomHandler.class.getName());
        assertThat((String) span.attributes.get("langfuse.observation.status_message"))
                .contains("IllegalStateException")
                .contains("synthetic boom");
        assertThat(span.statusSuccess).isFalse();

        DefaultHookChain.HookChainStats stats = chain.snapshot();
        assertThat(stats.firedByPhase()).containsEntry("PRE_REASONING", 1L);
        assertThat(stats.failuresByPhase()).containsEntry("PRE_REASONING", 1L);
        assertThat(stats.decisionsByOutcome()).containsEntry("ERROR", 1L);
    }

    @Test
    void firePhaseWithResult_abortDecision_isRecordedAsHookDecision() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultHookChain chain = new DefaultHookChain(tracer);
        chain.register(new AbortingHandler());

        StepVerifier.create(chain.firePreActingWithResult(new PreActingEvent("t", Map.of(), false)))
                .assertNext(r -> assertThat(r.decision()).isEqualTo(HookResult.Decision.ABORT))
                .verifyComplete();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.attributes)
                .containsEntry("hook.decision", "ABORT")
                .containsEntry("hook.phase", "PRE_ACTING");
        assertThat(chain.snapshot().decisionsByOutcome()).containsEntry("ABORT", 1L);
    }

    @Test
    void recordExternalHookFailure_emitsErrorSpanAndBumpsExternalCounter() {
        RecordingTracer tracer = new RecordingTracer();
        DefaultHookChain chain = new DefaultHookChain(tracer);

        chain.recordExternalHookFailure(
                HookPhase.PRE_ACTING.name(),
                "command:./guard.sh",
                new RuntimeException("script exited 1"));

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.attributes)
                .containsEntry("hook.phase", "PRE_ACTING")
                .containsEntry("hook.kind", "external")
                .containsEntry("hook.id", "command:./guard.sh")
                .containsEntry("langfuse.observation.level", "ERROR");
        assertThat(span.statusSuccess).isFalse();
        assertThat(chain.snapshot().externalHookFailures()).isEqualTo(1L);
        assertThat(chain.snapshot().failuresByPhase()).containsEntry("PRE_ACTING", 1L);
    }

    @Test
    void noTracerConstructor_isNoOpAndStillIncrementsCounters() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new GoodHandler());

        StepVerifier.create(chain.firePreReasoning(new PreReasoningEvent(List.of(), null, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(chain.snapshot().firedByPhase()).containsEntry("PRE_REASONING", 1L);
    }

    @Test
    void firePhase_inProcess_invokesObserverWithFiredCallback() {
        RecordingObserver observer = new RecordingObserver();
        HookChainObserver.setGlobal(observer);

        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new GoodHandler());

        StepVerifier.create(chain.firePreReasoning(new PreReasoningEvent(List.of(), null, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(observer.fired).hasSize(1);
        Fired f = observer.fired.get(0);
        assertThat(f.phase).isEqualTo("PRE_REASONING");
        assertThat(f.decision).isEqualTo("CONTINUE");
        assertThat(f.duration.toNanos()).isGreaterThanOrEqualTo(0L);
        assertThat(observer.failed).isEmpty();
        assertThat(observer.externalFailed).isEmpty();
    }

    @Test
    void firePhase_handlerThrows_invokesObserverFailedCallback() {
        RecordingObserver observer = new RecordingObserver();
        HookChainObserver.setGlobal(observer);

        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new BoomHandler());

        StepVerifier.create(chain.firePreReasoning(new PreReasoningEvent(List.of(), null, false)))
                .expectErrorMessage("synthetic boom")
                .verify();

        assertThat(observer.failed).hasSize(1);
        Failed f = observer.failed.get(0);
        assertThat(f.phase).isEqualTo("PRE_REASONING");
        assertThat(f.error).isInstanceOf(IllegalStateException.class);
        assertThat(observer.fired).isEmpty();
    }

    @Test
    void recordExternalHookFailure_invokesObserverExternalFailedCallback() {
        RecordingObserver observer = new RecordingObserver();
        HookChainObserver.setGlobal(observer);

        DefaultHookChain chain = new DefaultHookChain();
        chain.recordExternalHookFailure(
                HookPhase.PRE_ACTING.name(),
                "command:./guard.sh",
                new RuntimeException("script exited 1"));

        assertThat(observer.externalFailed).hasSize(1);
        ExternalFailed e = observer.externalFailed.get(0);
        assertThat(e.phase).isEqualTo("PRE_ACTING");
        assertThat(e.hookId).isEqualTo("command:./guard.sh");
        assertThat(e.error).isInstanceOf(RuntimeException.class);
    }

    @Test
    void observer_thatThrows_doesNotBreakChain() {
        HookChainObserver.setGlobal(
                new HookChainObserver() {
                    @Override
                    public void onHookFired(String phase, String decision, Duration duration) {
                        throw new RuntimeException("observer is buggy");
                    }

                    @Override
                    public void onHookFailed(String phase, Throwable error, Duration duration) {
                        throw new RuntimeException("observer is buggy");
                    }

                    @Override
                    public void onExternalHookFailure(
                            String phase, String hookId, Throwable error) {
                        throw new RuntimeException("observer is buggy");
                    }
                });

        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new GoodHandler());

        // A misbehaving observer must not propagate into the hook chain's reactive stream.
        StepVerifier.create(chain.firePreReasoning(new PreReasoningEvent(List.of(), null, false)))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(chain.snapshot().firedByPhase()).containsEntry("PRE_REASONING", 1L);
    }

    // ── Observer recording scaffolding ──────────────────────────────────────

    private record Fired(String phase, String decision, Duration duration) {}

    private record Failed(String phase, Throwable error, Duration duration) {}

    private record ExternalFailed(String phase, String hookId, Throwable error) {}

    private static final class RecordingObserver implements HookChainObserver {
        final List<Fired> fired = new ArrayList<>();
        final List<Failed> failed = new ArrayList<>();
        final List<ExternalFailed> externalFailed = new ArrayList<>();

        @Override
        public void onHookFired(String phase, String decision, Duration duration) {
            fired.add(new Fired(phase, decision, duration));
        }

        @Override
        public void onHookFailed(String phase, Throwable error, Duration duration) {
            failed.add(new Failed(phase, error, duration));
        }

        @Override
        public void onExternalHookFailure(String phase, String hookId, Throwable error) {
            externalFailed.add(new ExternalFailed(phase, hookId, error));
        }
    }

    // ── Recording scaffolding ───────────────────────────────────────────────

    private static final class RecordingTracer implements Tracer {
        final List<RecordingSpan> spans = new ArrayList<>();

        @Override
        public Span startHookSpan(Span parent, String phase, String hookName) {
            RecordingSpan span = new RecordingSpan("hook:" + phase + ":" + hookName);
            spans.add(span);
            return span;
        }

        @Override
        public void recordObservation(Span span, ObservationData data) {
            // Delegate to default impl so attribute conventions stay in one place.
            Tracer.super.recordObservation(span, data);
        }
    }

    private static final class RecordingSpan implements Span {
        final String name;
        final Map<String, Object> attributes = new HashMap<>();
        boolean statusSuccess;
        String statusMessage;
        boolean ended;

        RecordingSpan(String name) {
            this.name = name;
        }

        @Override
        public String spanId() {
            return "test-span";
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Span parent() {
            return NoopSpan.INSTANCE;
        }

        @Override
        public void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        @Override
        public void setStatus(boolean success, String message) {
            this.statusSuccess = success;
            this.statusMessage = message;
        }

        @Override
        public void end() {
            this.ended = true;
        }
    }
}
