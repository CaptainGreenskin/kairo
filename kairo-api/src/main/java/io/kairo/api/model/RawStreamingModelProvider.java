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

/**
 * Optional SPI extension for providers that can expose raw streaming chunks.
 *
 * <p>{@link ModelProvider#stream(List, ModelConfig)} emits provider-normalized partial model
 * responses. Some runtime features (e.g. eager tool execution) need lower-level provider frames.
 * Providers that support this mode should implement this interface.
 */
public interface RawStreamingModelProvider extends ModelProvider {

    /**
     * Stream provider-native chunks suitable for incremental tool-call detection.
     *
     * @param messages the conversation history
     * @param config model configuration
     * @return raw streaming chunks
     */
    Flux<StreamChunk> streamRaw(List<Msg> messages, ModelConfig config);
}
