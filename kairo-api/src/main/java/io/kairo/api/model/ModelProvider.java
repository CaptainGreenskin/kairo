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

import io.kairo.api.message.Msg;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SPI for invoking a language model.
 *
 * <p>Implementations wrap a specific LLM provider (Anthropic, OpenAI, etc.) and handle
 * authentication, request formatting, and response parsing.
 */
public interface ModelProvider {

    /**
     * Invoke the model and return a complete response.
     *
     * @param messages the conversation history
     * @param config model configuration (model name, tokens, tools, etc.)
     * @return a Mono emitting the model response
     */
    Mono<ModelResponse> call(List<Msg> messages, ModelConfig config);

    /**
     * Invoke the model in streaming mode.
     *
     * <p>Emits partial {@link ModelResponse} objects as they arrive from the provider.
     *
     * @param messages the conversation history
     * @param config model configuration
     * @return a Flux of partial responses
     */
    Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config);

    /**
     * The name of this provider (e.g. "anthropic", "openai").
     *
     * @return the provider name
     */
    String name();
}
