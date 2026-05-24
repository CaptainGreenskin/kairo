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
package io.kairo.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FallbackModelProviderTest {

    private static final List<Msg> MESSAGES = List.of(Msg.of(MsgRole.USER, "hi"));
    private static final ModelConfig CONFIG = ModelConfig.builder().model("test").build();

    @Test
    void primarySucceeds_fallbackNotInvoked() {
        var fb = new RecordingProvider("fb", null);
        var primary = new RecordingProvider("primary", null);
        var chain = new FallbackModelProvider(primary, List.of(fb));

        ModelResponse resp = chain.call(MESSAGES, CONFIG).block();

        assertThat(resp).isNotNull();
        assertThat(primary.calls).isEqualTo(1);
        assertThat(fb.calls).isEqualTo(0);
    }

    @Test
    void primaryRateLimited_fallbackTakesOver() {
        var primary =
                new RecordingProvider(
                        "primary", new ModelProviderException.RateLimitException("429", null));
        var fb = new RecordingProvider("fb", null);
        var chain = new FallbackModelProvider(primary, List.of(fb));

        ModelResponse resp = chain.call(MESSAGES, CONFIG).block();

        assertThat(resp).isNotNull();
        assertThat(primary.calls).isEqualTo(1);
        assertThat(fb.calls).isEqualTo(1);
    }

    @Test
    void chainExhausted_lastErrorPropagates() {
        var primary =
                new RecordingProvider(
                        "primary", new ModelProviderException.RateLimitException("p", null));
        var fb1 =
                new RecordingProvider(
                        "fb1", new ModelProviderException.RateLimitException("f1", null));
        var fb2 =
                new RecordingProvider(
                        "fb2", new ModelProviderException.RateLimitException("f2", null));
        var chain = new FallbackModelProvider(primary, List.of(fb1, fb2));

        assertThatThrownBy(() -> chain.call(MESSAGES, CONFIG).block(Duration.ofSeconds(2)))
                .hasMessageContaining("f2");
        assertThat(primary.calls).isEqualTo(1);
        assertThat(fb1.calls).isEqualTo(1);
        assertThat(fb2.calls).isEqualTo(1);
    }

    @Test
    void nonRetryableError_fallbackSkipped() {
        // Auth errors should not trigger fallback: predicate-default returns false
        // for ApiException without "5"/"server" in the message.
        var primary =
                new RecordingProvider(
                        "primary",
                        new ModelProviderException.ApiException("401 unauthorized", null));
        var fb = new RecordingProvider("fb", null);
        var chain = new FallbackModelProvider(primary, List.of(fb));

        assertThatThrownBy(() -> chain.call(MESSAGES, CONFIG).block(Duration.ofSeconds(2)))
                .hasMessageContaining("unauthorized");
        assertThat(fb.calls).isEqualTo(0);
    }

    @Test
    void name_reportsCompositeIdentity() {
        var chain =
                new FallbackModelProvider(
                        new RecordingProvider("p", null),
                        List.of(
                                new RecordingProvider("f1", null),
                                new RecordingProvider("f2", null)));
        assertThat(chain.name()).isEqualTo("fallback[p→f1→f2]");
    }

    @Test
    void emptyFallbackList_behavesLikePrimary() {
        var primary = new RecordingProvider("primary", null);
        var chain = new FallbackModelProvider(primary, List.of());
        ModelResponse resp = chain.call(MESSAGES, CONFIG).block();
        assertThat(resp).isNotNull();
        assertThat(chain.name()).isEqualTo("primary");
    }

    @Test
    void streamFallbackAlsoWorks() {
        var primary =
                new RecordingProvider(
                        "p", new ModelProviderException.RateLimitException("r", null));
        var fb = new RecordingProvider("fb", null);
        var chain = new FallbackModelProvider(primary, List.of(fb));

        var collected = chain.stream(MESSAGES, CONFIG).collectList().block();

        assertThat(collected).isNotNull().isNotEmpty();
        assertThat(fb.streamCalls).isEqualTo(1);
    }

    /** Test fixture: a ModelProvider that records calls and either succeeds or fails. */
    private static final class RecordingProvider implements ModelProvider {
        private final String name;
        private final Throwable failWith;
        int calls = 0;
        int streamCalls = 0;

        RecordingProvider(String name, Throwable failWith) {
            this.name = name;
            this.failWith = failWith;
        }

        @Override
        public Mono<ModelResponse> call(List<Msg> m, ModelConfig c) {
            calls++;
            if (failWith != null) return Mono.error(failWith);
            return Mono.just(
                    new ModelResponse(
                            "id-" + name,
                            List.of(),
                            new ModelResponse.Usage(0, 0, 0, 0),
                            ModelResponse.StopReason.END_TURN,
                            "test-model"));
        }

        @Override
        public Flux<ModelResponse> stream(List<Msg> m, ModelConfig c) {
            streamCalls++;
            if (failWith != null) return Flux.error(failWith);
            return Flux.just(
                    new ModelResponse(
                            "id-" + name,
                            List.of(),
                            new ModelResponse.Usage(0, 0, 0, 0),
                            ModelResponse.StopReason.END_TURN,
                            "test-model"));
        }

        @Override
        public String name() {
            return name;
        }
    }
}
