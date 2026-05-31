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

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Mixture-of-Agents (MoA) orchestrator: answers a single query by querying several models (the
 * <em>proposers</em>) in parallel and then asking an <em>aggregator</em> model to synthesize their
 * proposals into one high-quality response.
 *
 * <p>This differs from the expert-team coordinator: expert-team does role-specialized <em>task
 * decomposition</em> (research + draft + review), whereas MoA sends the <em>same</em> query to a
 * diverse set of models and fuses the answers. It is the Kairo equivalent of Together AI's
 * Mixture-of-Agents pattern.
 *
 * <p>This is a concrete, reusable capability class — it introduces no new SPI. It composes the
 * existing {@link ModelProvider} contract, so any provider registered upstream (Anthropic, OpenAI,
 * DeepSeek, GLM, …) can act as a proposer or aggregator. A proposer carries its own provider, so
 * cross-provider ensembles are supported.
 *
 * <p><strong>Resilience:</strong> a proposer that errors does not fail the run — its slot is
 * recorded with {@code ok=false} and the aggregator synthesizes from the successful proposals. The
 * run only fails if <em>every</em> proposer fails.
 *
 * <p><strong>Thread safety:</strong> instances are immutable and safe to reuse across calls.
 *
 * @since 1.4.0
 */
public final class MixtureOfAgentsCoordinator {

    private static final Logger log = LoggerFactory.getLogger(MixtureOfAgentsCoordinator.class);

    /** Default synthesis instruction, adapted from the canonical MoA aggregation prompt. */
    public static final String DEFAULT_AGGREGATION_INSTRUCTION =
            "You have been provided with a set of responses from several AI models to the user"
                    + " query below. Your task is to synthesize these into a single, high-quality"
                    + " answer. Critically evaluate the responses, recognizing that some may be biased"
                    + " or incorrect; do not simply copy, but integrate the best, most accurate"
                    + " information into a coherent, well-structured reply. If the responses disagree,"
                    + " reason about which is correct. Respond only with the final synthesized answer.";

    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final double DEFAULT_PROPOSER_TEMPERATURE = 0.7;
    private static final double DEFAULT_AGGREGATOR_TEMPERATURE = 0.3;

    private final List<Proposer> proposers;
    private final Proposer aggregator;
    private final String aggregationInstruction;
    private final int maxTokens;
    private final double proposerTemperature;
    private final double aggregatorTemperature;

    private MixtureOfAgentsCoordinator(Builder b) {
        if (b.proposers.isEmpty()) {
            throw new IllegalArgumentException("MoA requires at least one proposer");
        }
        this.proposers = List.copyOf(b.proposers);
        this.aggregator = Objects.requireNonNull(b.aggregator, "aggregator");
        this.aggregationInstruction =
                b.aggregationInstruction == null || b.aggregationInstruction.isBlank()
                        ? DEFAULT_AGGREGATION_INSTRUCTION
                        : b.aggregationInstruction;
        this.maxTokens = b.maxTokens;
        this.proposerTemperature = b.proposerTemperature;
        this.aggregatorTemperature = b.aggregatorTemperature;
    }

    /**
     * Create a new builder.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Run the mixture-of-agents flow for the given query.
     *
     * @param query the user query to answer
     * @return a Mono emitting the synthesized result
     */
    public Mono<MoaResult> run(String query) {
        if (query == null || query.isBlank()) {
            return Mono.error(new IllegalArgumentException("query must not be blank"));
        }
        return Flux.fromIterable(proposers)
                .flatMap(p -> callProposer(p, query))
                .collectList()
                .flatMap(
                        proposals -> {
                            boolean anyOk = proposals.stream().anyMatch(Proposal::ok);
                            if (!anyOk) {
                                return Mono.error(
                                        new IllegalStateException(
                                                "all "
                                                        + proposals.size()
                                                        + " MoA proposers failed"));
                            }
                            return aggregate(query, proposals);
                        });
    }

    private Mono<Proposal> callProposer(Proposer p, String query) {
        ModelConfig config =
                ModelConfig.builder()
                        .model(p.model())
                        .maxTokens(maxTokens)
                        .temperature(proposerTemperature)
                        .build();
        List<Msg> msgs = List.of(Msg.of(MsgRole.USER, query));
        return p.provider()
                .call(msgs, config)
                .map(r -> new Proposal(p.label(), p.model(), extractText(r), true, null))
                .onErrorResume(
                        e -> {
                            log.warn(
                                    "MoA proposer '{}' (model={}) failed: {}",
                                    p.label(),
                                    p.model(),
                                    e.toString());
                            return Mono.just(
                                    new Proposal(p.label(), p.model(), "", false, e.toString()));
                        });
    }

    private Mono<MoaResult> aggregate(String query, List<Proposal> proposals) {
        StringBuilder sb = new StringBuilder();
        sb.append(aggregationInstruction).append("\n\n");
        int idx = 1;
        for (Proposal pr : proposals) {
            if (!pr.ok() || pr.text().isBlank()) {
                continue;
            }
            sb.append("### Response ")
                    .append(idx++)
                    .append(" (model: ")
                    .append(pr.model())
                    .append(")\n")
                    .append(pr.text())
                    .append("\n\n");
        }
        sb.append("### User query\n").append(query).append("\n");

        ModelConfig config =
                ModelConfig.builder()
                        .model(aggregator.model())
                        .maxTokens(maxTokens)
                        .temperature(aggregatorTemperature)
                        .build();
        List<Msg> msgs = List.of(Msg.of(MsgRole.USER, sb.toString()));
        return aggregator
                .provider()
                .call(msgs, config)
                .map(r -> new MoaResult(extractText(r), proposals, aggregator.model()));
    }

    private static String extractText(io.kairo.api.model.ModelResponse r) {
        return r.contents().stream()
                .filter(Content.TextContent.class::isInstance)
                .map(Content.TextContent.class::cast)
                .map(Content.TextContent::text)
                .collect(Collectors.joining("\n"));
    }

    /**
     * A proposer model: a {@link ModelProvider} plus the concrete model name it should run, and a
     * human-readable label for reporting.
     *
     * @param provider the model provider to invoke
     * @param model the concrete model name
     * @param label a short human-readable label (e.g. "proposer-1")
     */
    public record Proposer(ModelProvider provider, String model, String label) {
        public Proposer {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(model, "model");
            if (label == null || label.isBlank()) {
                label = model;
            }
        }
    }

    /**
     * One proposer's contribution.
     *
     * @param label the proposer label
     * @param model the model name
     * @param text the proposed answer text (empty when {@code ok} is false)
     * @param ok whether the proposer succeeded
     * @param error the error description when {@code ok} is false, otherwise null
     */
    public record Proposal(String label, String model, String text, boolean ok, String error) {}

    /**
     * The synthesized result.
     *
     * @param answer the aggregator's final synthesized answer
     * @param proposals the individual proposer contributions (including failures)
     * @param aggregatorModel the model used to aggregate
     */
    public record MoaResult(String answer, List<Proposal> proposals, String aggregatorModel) {
        public MoaResult {
            proposals = List.copyOf(proposals);
        }

        /**
         * Number of proposers that returned a usable answer.
         *
         * @return the count of successful proposals
         */
        public long successfulProposerCount() {
            return proposals.stream().filter(Proposal::ok).count();
        }
    }

    /** Builder for {@link MixtureOfAgentsCoordinator}. */
    public static final class Builder {
        private final List<Proposer> proposers = new ArrayList<>();
        private Proposer aggregator;
        private String aggregationInstruction;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private double proposerTemperature = DEFAULT_PROPOSER_TEMPERATURE;
        private double aggregatorTemperature = DEFAULT_AGGREGATOR_TEMPERATURE;

        /**
         * Add a proposer.
         *
         * @param provider the model provider
         * @param model the model name
         * @param label a short label
         * @return this builder
         */
        public Builder addProposer(ModelProvider provider, String model, String label) {
            this.proposers.add(new Proposer(provider, model, label));
            return this;
        }

        /**
         * Add a proposer.
         *
         * @param proposer the proposer
         * @return this builder
         */
        public Builder addProposer(Proposer proposer) {
            this.proposers.add(Objects.requireNonNull(proposer, "proposer"));
            return this;
        }

        /**
         * Set the aggregator model.
         *
         * @param provider the model provider
         * @param model the model name
         * @return this builder
         */
        public Builder aggregator(ModelProvider provider, String model) {
            this.aggregator = new Proposer(provider, model, "aggregator");
            return this;
        }

        /**
         * Override the synthesis instruction.
         *
         * @param instruction the aggregation instruction
         * @return this builder
         */
        public Builder aggregationInstruction(String instruction) {
            this.aggregationInstruction = instruction;
            return this;
        }

        /**
         * Set the max output tokens for both proposer and aggregator calls.
         *
         * @param maxTokens the token cap
         * @return this builder
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Set the proposer sampling temperature.
         *
         * @param t the temperature
         * @return this builder
         */
        public Builder proposerTemperature(double t) {
            this.proposerTemperature = t;
            return this;
        }

        /**
         * Set the aggregator sampling temperature.
         *
         * @param t the temperature
         * @return this builder
         */
        public Builder aggregatorTemperature(double t) {
            this.aggregatorTemperature = t;
            return this;
        }

        /**
         * Build the coordinator.
         *
         * @return a new immutable coordinator
         */
        public MixtureOfAgentsCoordinator build() {
            return new MixtureOfAgentsCoordinator(this);
        }
    }
}
