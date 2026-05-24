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
package io.kairo.mcp;

import reactor.core.publisher.Mono;

/**
 * SPI for handling MCP server elicitation requests.
 *
 * <p>Elicitation allows an MCP server to request additional user input during tool execution. This
 * is an independent SPI (NOT a Hook) — Elicitation is MCP protocol-level, defined in the MCP
 * specification.
 *
 * <p>Implementations receive an {@link ElicitationRequest} describing what the server needs and
 * return an {@link ElicitationResponse} with the user's decision and any provided data.
 *
 * <p>Example:
 *
 * <pre>{@code
 * ElicitationHandler handler = request -> {
 *     // Present request.message() to the user and collect input
 *     Map<String, Object> userData = collectUserInput(request.requestedSchema());
 *     return Mono.just(ElicitationResponse.accept(userData));
 * };
 * }</pre>
 *
 * @apiNote Experimental — this SPI may change in minor versions as the MCP elicitation protocol
 *     evolves. Marked for potential promotion to Stable once the MCP specification stabilizes
 *     elicitation semantics.
 * @implSpec Implementations receive an {@link ElicitationRequest} and must return a non-null {@link
 *     ElicitationResponse}. Implementations may block waiting for user input; the returned {@link
 *     reactor.core.publisher.Mono} should complete when the user responds or a timeout occurs.
 *     Return {@link ElicitationResponse#decline()} for unrecognized requests.
 * @see ElicitationRequest
 * @see ElicitationResponse
 * @see DevOnlyAutoApproveHandler
 * @see AutoDeclineElicitationHandler
 * @since 0.5.0
 */
@FunctionalInterface
public interface ElicitationHandler {

    /**
     * Handles an elicitation request from an MCP server.
     *
     * @param request the elicitation request containing the server's message and requested schema
     * @return a Mono emitting the elicitation response with the user's decision and data
     */
    Mono<ElicitationResponse> handle(ElicitationRequest request);
}
