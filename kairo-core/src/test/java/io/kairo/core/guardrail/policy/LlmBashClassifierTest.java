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
package io.kairo.core.guardrail.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tracing.NoopSpan;
import io.kairo.api.tracing.Span;
import io.kairo.api.tracing.Tracer;
import io.kairo.core.guardrail.policy.BashCommandClassifier.Category;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Behavioural + observability tests for {@link LlmBashClassifier}.
 *
 * <p>The 8 behavioural tests pin down the happy path (heuristic short-circuit, cache, parse
 * failures, LLM errors, timeout). The 4 observability tests are the differentiator for this "model
 * citizen" slice — they assert that the Tracer span carries the full {@code langfuse.observation.*}
 * + {@code gen_ai.usage.*} attribute set, that the {@link io.kairo.api.guardrail.SecurityEventSink}
 * sees structured audit records, and that {@link LlmBashClassifier#snapshot()} reports counters
 * consistent with the dispatched calls.
 */
class LlmBashClassifierTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Behavioural tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void heuristicNonUnknown_doesNotCallLlm() {
        ModelProvider provider = mock(ModelProvider.class);
        var classifier = new LlmBashClassifier(provider, "test-model");

        Category verdict = classifier.classify("ls -la").block();

        assertThat(verdict).isEqualTo(Category.READ_ONLY);
        verify(provider, never()).call(any(), any());
        assertThat(classifier.snapshot().llmCalls()).isZero();
    }

    @Test
    void heuristicUnknown_llmReturnsDestructive_yieldsDestructive() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any()))
                .thenReturn(
                        Mono.just(jsonResponse("{\"category\":\"DESTRUCTIVE\",\"reason\":\"x\"}")));

        var classifier = new LlmBashClassifier(provider, "test-model");
        Category verdict = classifier.classify("./obscure-script.sh").block();

        assertThat(verdict).isEqualTo(Category.DESTRUCTIVE);
        verify(provider, times(1)).call(any(), any());
    }

    @Test
    void heuristicUnknown_llmReturnsGarbage_degradesToUnknown() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any())).thenReturn(Mono.just(jsonResponse("this is not json")));

        var classifier = new LlmBashClassifier(provider, "test-model");
        Category verdict = classifier.classify("./mystery.sh").block();

        assertThat(verdict).isEqualTo(Category.UNKNOWN);
        // Parse-failure UNKNOWN counts against llmFailures so it surfaces in dashboards.
        assertThat(classifier.snapshot().llmFailures()).isEqualTo(1L);
    }

    @Test
    void heuristicUnknown_llmReturnsEmptyContent_degradesToUnknown() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any())).thenReturn(Mono.just(emptyResponse()));

        var classifier = new LlmBashClassifier(provider, "test-model");
        Category verdict = classifier.classify("./mystery.sh").block();

        assertThat(verdict).isEqualTo(Category.UNKNOWN);
    }

    @Test
    void heuristicUnknown_providerThrowsSynchronously_degradesToUnknown() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any())).thenThrow(new IllegalStateException("boom"));

        var classifier = new LlmBashClassifier(provider, "test-model");
        Category verdict = classifier.classify("./mystery.sh").block();

        assertThat(verdict).isEqualTo(Category.UNKNOWN);
        assertThat(classifier.snapshot().llmFailures()).isEqualTo(1L);
    }

    @Test
    void heuristicUnknown_providerReturnsMonoError_degradesToUnknown() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("network down")));

        var classifier = new LlmBashClassifier(provider, "test-model");
        Category verdict = classifier.classify("./mystery.sh").block();

        assertThat(verdict).isEqualTo(Category.UNKNOWN);
        assertThat(classifier.snapshot().llmFailures()).isEqualTo(1L);
    }

    @Test
    void heuristicUnknown_llmTimesOut_degradesToUnknown() {
        ModelProvider provider = mock(ModelProvider.class);
        // Provider that never emits — only the outer .timeout(...) will fire it.
        when(provider.call(any(), any())).thenReturn(Mono.never());

        // Short timeout so we don't need virtual-time to keep the test snappy; the production
        // default is 5s but tests are free to dial it down.
        var classifier =
                new LlmBashClassifier(
                        provider,
                        "test-model",
                        LlmBashClassifier.Config.builder().timeout(Duration.ofMillis(50)).build());

        StepVerifier.create(classifier.classify("./mystery.sh"))
                .expectNext(Category.UNKNOWN)
                .verifyComplete();

        assertThat(classifier.snapshot().llmFailures()).isEqualTo(1L);
    }

    @Test
    void heuristicUnknown_secondCallHitsCache_skipsLlm() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any()))
                .thenReturn(Mono.just(jsonResponse("{\"category\":\"EXEC\",\"reason\":\"x\"}")));

        var classifier = new LlmBashClassifier(provider, "test-model");
        Category first = classifier.classify("./mystery.sh").block();
        Category second = classifier.classify("./mystery.sh").block();

        assertThat(first).isEqualTo(Category.EXEC);
        assertThat(second).isEqualTo(Category.EXEC);
        verify(provider, times(1)).call(any(), any());

        var stats = classifier.snapshot();
        assertThat(stats.cacheHits()).isEqualTo(1L);
        assertThat(stats.cacheMisses()).isEqualTo(1L);
        assertThat(stats.llmCalls()).isEqualTo(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observability tests — the differentiator for the model-citizen slice.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void llmCall_emitsSpanWithLangfuseAttributes() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any()))
                .thenReturn(
                        Mono.just(
                                jsonResponseWithUsage(
                                        "{\"category\":\"DESTRUCTIVE\",\"reason\":\"x\"}", 42, 7)));

        var tracer = new RecordingTracer();
        var classifier =
                new LlmBashClassifier(
                        provider,
                        "test-model",
                        LlmBashClassifier.Config.builder().tracer(tracer).build());

        classifier.classify("./obscure.sh").block();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.attributes)
                .containsKeys(
                        "classifier.heuristic_verdict",
                        "classifier.llm_verdict",
                        "classifier.latency_ms",
                        "classifier.model",
                        "classifier.command_prefix",
                        "classifier.command_length",
                        "langfuse.observation.type",
                        "langfuse.observation.model",
                        "langfuse.observation.input",
                        "langfuse.observation.output",
                        "langfuse.observation.level",
                        "langfuse.usage_details",
                        "gen_ai.usage.input_tokens",
                        "gen_ai.usage.output_tokens");
        assertThat(span.attributes.get("classifier.heuristic_verdict")).isEqualTo("UNKNOWN");
        assertThat(span.attributes.get("classifier.llm_verdict")).isEqualTo("DESTRUCTIVE");
        assertThat(span.attributes.get("langfuse.observation.type")).isEqualTo("generation");
        assertThat(span.attributes.get("langfuse.observation.level")).isEqualTo("DEFAULT");
        assertThat(span.attributes.get("gen_ai.usage.input_tokens")).isEqualTo(42);
        assertThat(span.attributes.get("gen_ai.usage.output_tokens")).isEqualTo(7);
        assertThat(span.statusSuccess).isTrue();
        assertThat(span.ended).isTrue();
    }

    @Test
    void llmCall_emitsStructuredSecurityEvent() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any()))
                .thenReturn(
                        Mono.just(
                                jsonResponse(
                                        "{\"category\":\"DESTRUCTIVE\",\"reason\":\"wipes things\"}")));

        List<SecurityEvent> events = new ArrayList<>();
        var classifier =
                new LlmBashClassifier(
                        provider,
                        "test-model",
                        LlmBashClassifier.Config.builder().sink(events::add).build());

        classifier.classify("./obscure.sh").block();

        assertThat(events).hasSize(1);
        SecurityEvent ev = events.get(0);
        assertThat(ev.policyName()).isEqualTo("LlmBashClassifier");
        assertThat(ev.targetName()).startsWith("bash:");
        assertThat(ev.attributes())
                .containsEntry("verdict", "DESTRUCTIVE")
                .containsEntry("heuristic_verdict", "UNKNOWN")
                .containsEntry("model", "test-model")
                .containsEntry("cache_hit", false)
                .containsKeys("latency_ms", "tokens_in", "tokens_out", "command_length");
    }

    @Test
    void llmFailure_setsErrorLevelAndEmitsFailureEvent() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("upstream 503")));

        var tracer = new RecordingTracer();
        List<SecurityEvent> events = new ArrayList<>();
        var classifier =
                new LlmBashClassifier(
                        provider,
                        "test-model",
                        LlmBashClassifier.Config.builder()
                                .tracer(tracer)
                                .sink(events::add)
                                .build());

        classifier.classify("./mystery.sh").block();

        assertThat(tracer.spans).hasSize(1);
        RecordingSpan span = tracer.spans.get(0);
        assertThat(span.attributes.get("langfuse.observation.level")).isEqualTo("ERROR");
        assertThat(span.attributes)
                .containsKey("langfuse.observation.status_message")
                .containsKey("classifier.latency_ms");
        assertThat(span.statusSuccess).isFalse();
        assertThat(span.ended).isTrue();

        assertThat(events).hasSize(1);
        SecurityEvent ev = events.get(0);
        assertThat(ev.reason()).contains("failed");
        assertThat(ev.attributes())
                .containsEntry("verdict", "UNKNOWN")
                .containsEntry("model", "test-model")
                .containsKeys("failure_class", "failure_message", "command_length");
    }

    @Test
    void snapshot_tracksVerdictDistributionAndCounters() {
        ModelProvider provider = mock(ModelProvider.class);
        when(provider.call(any(), any()))
                .thenReturn(
                        Mono.just(
                                jsonResponseWithUsage(
                                        "{\"category\":\"WRITE\",\"reason\":\"x\"}", 10, 4)));

        var classifier = new LlmBashClassifier(provider, "test-model");

        // 2 heuristic-resolved (READ_ONLY), 1 LLM-resolved (WRITE), 1 cache-hit.
        classifier.classify("ls").block();
        classifier.classify("cat foo").block();
        classifier.classify("./mystery.sh").block();
        classifier.classify("./mystery.sh").block();

        var stats = classifier.snapshot();
        assertThat(stats.verdictCounts().get(Category.READ_ONLY)).isEqualTo(2L);
        assertThat(stats.verdictCounts().get(Category.WRITE)).isEqualTo(2L);
        assertThat(stats.llmCalls()).isEqualTo(1L);
        assertThat(stats.cacheHits()).isEqualTo(1L);
        assertThat(stats.cacheMisses()).isEqualTo(1L);
        assertThat(stats.totalInputTokens()).isEqualTo(10L);
        assertThat(stats.totalOutputTokens()).isEqualTo(4L);
        assertThat(stats.llmFailures()).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test fixtures
    // ─────────────────────────────────────────────────────────────────────────

    /** Build a ModelResponse whose first text content is the given JSON string. */
    private static ModelResponse jsonResponse(String text) {
        return new ModelResponse(
                "resp-id",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(0, 0, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    private static ModelResponse jsonResponseWithUsage(String text, int inTokens, int outTokens) {
        return new ModelResponse(
                "resp-id",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(inTokens, outTokens, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    /** ModelResponse with zero content blocks — exercises the empty-content path. */
    private static ModelResponse emptyResponse() {
        return new ModelResponse(
                "resp-id",
                List.of(),
                new ModelResponse.Usage(0, 0, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    /**
     * Minimal Tracer that captures every {@link Span} returned by {@link Tracer#startReasoningSpan}
     * so tests can assert on attributes / status without needing the production
     * StructuredLogTracer.
     */
    private static final class RecordingTracer implements Tracer {
        final List<RecordingSpan> spans = new ArrayList<>();

        @Override
        public Span startReasoningSpan(Span parent, String modelName, int messageCount) {
            RecordingSpan span = new RecordingSpan("reasoning:" + modelName);
            spans.add(span);
            return span;
        }
    }

    /** Span recording every {@code setAttribute}, status update, and {@code end()} call. */
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
