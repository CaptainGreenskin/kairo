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
package io.kairo.api.context;

import io.kairo.api.message.Msg;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * SPI for context compaction strategies.
 *
 * <p>Multiple strategies can be registered; they are tried in priority order (lower value = tried
 * first). A strategy decides when compaction should trigger and how to compact the message list.
 */
public interface CompactionStrategy {

    /**
     * Whether this strategy should trigger compaction given the current context state.
     *
     * @param state the current context state
     * @return true if compaction should be triggered
     */
    boolean shouldTrigger(ContextState state);

    /**
     * Perform compaction on the given messages.
     *
     * @param messages the messages to compact
     * @param config the compaction configuration
     * @return a Mono emitting the compaction result
     */
    Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig config);

    /**
     * Priority of this strategy. Lower values are tried first.
     *
     * @return the priority value
     */
    int priority();

    /**
     * The name of this compaction strategy.
     *
     * @return the strategy name
     */
    String name();
}
