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
package io.kairo.multiagent.moa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MixtureOfAgentsCoordinatorTest {

    /** A fake provider that echoes a fixed answer per model, or errors for a sentinel model. */
    private static ModelProvider fake(String name) {
        return new ModelProvider() {
            @Override
            public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                if ("boom".equals(config.model())) {
                    return Mono.error(new RuntimeException("simulated failure"));
                }
                String userText = messages.get(messages.size() - 1).text();
                String body =
                        "aggregator".equals(config.model())
                                ? "SYNTHESIZED[" + userText.length() + "]"
                                : "answer-from-" + config.model();
                return Mono.just(
                        new ModelResponse(
                                "id",
                                List.of(new Content.TextContent(body)),
                                new ModelResponse.Usage(0, 0, 0, 0),
                                ModelResponse.StopReason.END_TURN,
                                config.model()));
            }

            @Override
            public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                return call(messages, config).flux();
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    @Test
    void synthesizesFromMultipleProposers() {
        ModelProvider p = fake("test");
        MixtureOfAgentsCoordinator moa =
                MixtureOfAgentsCoordinator.builder()
                        .addProposer(p, "model-a", "a")
                        .addProposer(p, "model-b", "b")
                        .aggregator(p, "aggregator")
                        .build();

        StepVerifier.create(moa.run("what is 2+2?"))
                .assertNext(
                        res -> {
                            assertThat(res.answer()).startsWith("SYNTHESIZED");
                            assertThat(res.proposals()).hasSize(2);
                            assertThat(res.successfulProposerCount()).isEqualTo(2);
                            assertThat(res.aggregatorModel()).isEqualTo("aggregator");
                        })
                .verifyComplete();
    }

    @Test
    void survivesPartialProposerFailure() {
        ModelProvider p = fake("test");
        MixtureOfAgentsCoordinator moa =
                MixtureOfAgentsCoordinator.builder()
                        .addProposer(p, "model-a", "a")
                        .addProposer(p, "boom", "b")
                        .aggregator(p, "aggregator")
                        .build();

        StepVerifier.create(moa.run("hi"))
                .assertNext(
                        res -> {
                            assertThat(res.successfulProposerCount()).isEqualTo(1);
                            assertThat(res.proposals()).anyMatch(pr -> !pr.ok());
                            assertThat(res.answer()).startsWith("SYNTHESIZED");
                        })
                .verifyComplete();
    }

    @Test
    void failsWhenAllProposersFail() {
        ModelProvider p = fake("test");
        MixtureOfAgentsCoordinator moa =
                MixtureOfAgentsCoordinator.builder()
                        .addProposer(p, "boom", "a")
                        .aggregator(p, "aggregator")
                        .build();

        StepVerifier.create(moa.run("hi")).verifyError(IllegalStateException.class);
    }

    @Test
    void rejectsBlankQuery() {
        ModelProvider p = fake("test");
        MixtureOfAgentsCoordinator moa =
                MixtureOfAgentsCoordinator.builder()
                        .addProposer(p, "model-a", "a")
                        .aggregator(p, "aggregator")
                        .build();

        StepVerifier.create(moa.run("  ")).verifyError(IllegalArgumentException.class);
    }

    @Test
    void requiresAtLeastOneProposer() {
        ModelProvider p = fake("test");
        assertThatThrownBy(
                        () ->
                                MixtureOfAgentsCoordinator.builder()
                                        .aggregator(p, "aggregator")
                                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
