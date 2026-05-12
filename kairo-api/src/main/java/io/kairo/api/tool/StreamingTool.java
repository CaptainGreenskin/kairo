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
package io.kairo.api.tool;

import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * A tool that produces a stream of events (progress, chunks, and a final result).
 *
 * <p>The {@link ToolExecutor} passes the returned {@link Flux} through directly when dispatching
 * streaming tools.
 *
 * @since 1.2.0
 */
public interface StreamingTool {

    /**
     * Execute this tool as a stream of events.
     *
     * @param args the input parameters parsed from the LLM's tool-use request
     * @param ctx the runtime context (agent ID, session ID, dependencies, budget, etc.)
     * @return a Flux of tool events, terminated by a {@link ToolEvent.Final}
     */
    Flux<ToolEvent> stream(Map<String, Object> args, ToolContext ctx);
}
