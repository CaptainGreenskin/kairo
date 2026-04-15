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
import reactor.core.publisher.Mono;

/**
 * Structured output parser that extracts typed objects from model responses.
 *
 * @param <T> the target type to parse into
 */
public interface StructuredOutput<T> {

    /**
     * Parse a JSON string into the target type.
     *
     * @param json the JSON string
     * @param type the target class
     * @return the parsed object
     */
    T parse(String json, Class<T> type);

    /**
     * Call the model and parse the response into a structured object.
     *
     * @param provider the model provider to use
     * @param messages the conversation history
     * @param config model configuration
     * @param type the target class
     * @return a Mono emitting the parsed object
     */
    Mono<T> callAndParse(
            ModelProvider provider, List<Msg> messages, ModelConfig config, Class<T> type);
}
