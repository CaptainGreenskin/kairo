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
package io.kairo.api.model;

import io.kairo.api.Experimental;

/**
 * Structural SPI capturing the four stages every {@link ModelProvider} decomposes into. Named
 * stages map directly onto the ADR-005 provider decomposition template:
 *
 * <ul>
 *   <li>{@link RequestBuilder}: assembles an {@link io.kairo.api.model.ModelConfig} into the
 *       provider-specific request payload.
 *   <li>{@link ResponseParser}: parses the provider response into a canonical {@link
 *       io.kairo.api.model.ModelResponse}.
 *   <li>{@link StreamSubscriber}: consumes provider SSE / streaming chunks.
 *   <li>{@link ErrorClassifier}: normalizes provider error responses into Kairo exceptions.
 * </ul>
 *
 * <p>v0.10 introduces the SPI alongside the existing concrete Anthropic and OpenAI implementations;
 * actual migration of those providers to depend on this pipeline is tracked as a follow-up task in
 * {@code docs/roadmap/v0.10-core-refactor-verification.md}.
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Provider pipeline — API may change in v0.11")
public interface ProviderPipeline<RequestT, ResponseT> {

    RequestBuilder<RequestT> requestBuilder();

    ResponseParser<ResponseT> responseParser();

    StreamSubscriber<ResponseT> streamSubscriber();

    ErrorClassifier errorClassifier();

    /** Builds a provider-specific request payload. */
    @FunctionalInterface
    interface RequestBuilder<RequestT> {
        RequestT build(ModelConfig config);
    }

    /** Converts a provider response payload into a canonical {@link ModelResponse}. */
    @FunctionalInterface
    interface ResponseParser<ResponseT> {
        ModelResponse parse(ResponseT raw);
    }

    /** Consumes provider streaming chunks; implementations typically accumulate tool calls. */
    @FunctionalInterface
    interface StreamSubscriber<ResponseT> {
        void onChunk(ResponseT chunk);
    }

    /** Normalises provider errors into {@link ClassifiedError}. */
    @FunctionalInterface
    interface ErrorClassifier {
        ClassifiedError classify(Throwable error);
    }
}
