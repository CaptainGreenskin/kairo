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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Executes tools by name with given input parameters. */
public interface ToolExecutor {

    /**
     * Execute a tool with the given input.
     *
     * @param toolName the name of the tool to execute
     * @param input the input parameters
     * @return a Mono emitting the tool result
     */
    Mono<ToolResult> execute(String toolName, Map<String, Object> input);

    /**
     * Execute a tool with a timeout.
     *
     * @param toolName the tool name
     * @param input the input parameters
     * @param timeout the maximum execution duration
     * @return a Mono emitting the tool result
     */
    Mono<ToolResult> execute(String toolName, Map<String, Object> input, Duration timeout);

    /**
     * Execute multiple tool invocations in parallel.
     *
     * @param invocations the tool invocations to execute
     * @return a Flux emitting results as they complete
     */
    Flux<ToolResult> executeParallel(List<ToolInvocation> invocations);
}
