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
package io.kairo.core.a2a.remote;

import io.kairo.api.a2a.A2aClient;
import io.kairo.api.a2a.A2aException;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Server-side handler for A2A HTTP requests. Framework-agnostic — callers bridge from Spring
 * WebFlux, Vert.x, or raw servlet.
 *
 * <p>The handler delegates to a local {@link A2aClient} (typically {@link
 * io.kairo.core.a2a.InProcessA2aClient}) to dispatch requests to in-process agents.
 *
 * <p>Authentication is delegated to a configurable token validator predicate.
 */
public final class A2aEndpointHandler {

    private static final Logger log = LoggerFactory.getLogger(A2aEndpointHandler.class);

    private final A2aClient localClient;
    private final @Nullable Predicate<String> tokenValidator;

    public A2aEndpointHandler(A2aClient localClient, @Nullable Predicate<String> tokenValidator) {
        this.localClient = Objects.requireNonNull(localClient, "localClient must not be null");
        this.tokenValidator = tokenValidator;
    }

    public A2aEndpointHandler(A2aClient localClient) {
        this(localClient, null);
    }

    /**
     * Handle a send (request-response) invocation.
     *
     * @param requestJson raw JSON body
     * @param authToken the bearer token (may be null)
     * @return JSON response string
     */
    public Mono<String> handleSend(String requestJson, @Nullable String authToken) {
        return Mono.defer(
                () -> {
                    if (!validateToken(authToken)) {
                        return Mono.just(
                                A2aResponse.error("UNAUTHORIZED", "Invalid or missing token")
                                        .toJson());
                    }

                    A2aRequest request;
                    try {
                        request = A2aRequest.fromJson(requestJson);
                    } catch (Exception e) {
                        return Mono.just(
                                A2aResponse.error(
                                                "BAD_REQUEST", "Invalid request: " + e.getMessage())
                                        .toJson());
                    }

                    log.debug(
                            "A2A send to agent '{}' from message id={}",
                            request.targetAgentId(),
                            request.message().id());

                    return localClient
                            .send(request.targetAgentId(), request.message())
                            .map(response -> A2aResponse.ok(response).toJson())
                            .onErrorResume(
                                    A2aException.class,
                                    e -> {
                                        log.warn("A2A send failed: {}", e.getMessage());
                                        return Mono.just(
                                                A2aResponse.error("AGENT_ERROR", e.getMessage())
                                                        .toJson());
                                    })
                            .onErrorResume(
                                    e -> {
                                        log.error("A2A send unexpected error", e);
                                        return Mono.just(
                                                A2aResponse.error("INTERNAL_ERROR", e.getMessage())
                                                        .toJson());
                                    });
                });
    }

    /**
     * Handle a stream invocation. Returns SSE-style newline-delimited JSON.
     *
     * @param requestJson raw JSON body
     * @param authToken the bearer token (may be null)
     * @return flux of SSE data lines (each line is "data: {json}\n")
     */
    public Flux<String> handleStream(String requestJson, @Nullable String authToken) {
        return Flux.defer(
                () -> {
                    if (!validateToken(authToken)) {
                        return Flux.just(
                                "data: "
                                        + A2aResponse.error(
                                                        "UNAUTHORIZED", "Invalid or missing token")
                                                .toJson()
                                        + "\n");
                    }

                    A2aRequest request;
                    try {
                        request = A2aRequest.fromJson(requestJson);
                    } catch (Exception e) {
                        return Flux.just(
                                "data: "
                                        + A2aResponse.error(
                                                        "BAD_REQUEST",
                                                        "Invalid request: " + e.getMessage())
                                                .toJson()
                                        + "\n");
                    }

                    log.debug(
                            "A2A stream to agent '{}' from message id={}",
                            request.targetAgentId(),
                            request.message().id());

                    return localClient.stream(request.targetAgentId(), request.message())
                            .map(msg -> "data: " + A2aResponse.ok(msg).toJson() + "\n")
                            .onErrorResume(
                                    e -> {
                                        log.warn("A2A stream error: {}", e.getMessage());
                                        return Flux.just(
                                                "data: "
                                                        + A2aResponse.error(
                                                                        "STREAM_ERROR",
                                                                        e.getMessage())
                                                                .toJson()
                                                        + "\n");
                                    });
                });
    }

    /**
     * Health check for the A2A endpoint.
     *
     * @return JSON status string
     */
    public String healthCheck() {
        return "{\"status\":\"ok\",\"protocol\":\"a2a\",\"version\":\"1.0\"}";
    }

    private boolean validateToken(@Nullable String token) {
        if (tokenValidator == null) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        return tokenValidator.test(token);
    }
}
