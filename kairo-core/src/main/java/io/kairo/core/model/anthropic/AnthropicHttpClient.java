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
package io.kairo.core.model.anthropic;

import io.kairo.core.model.ModelProviderException;
import io.kairo.core.model.ModelProviderUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Handles HTTP communication with the Anthropic Messages API.
 *
 * <p>Owns the {@link HttpClient}, request building, response status dispatch, and retry-after
 * header parsing. Callers provide the JSON body and receive either raw response strings or
 * subscribe to SSE line streams.
 */
public class AnthropicHttpClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicHttpClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    /**
     * Create a new Anthropic HTTP client.
     *
     * @param httpClient the JDK HTTP client
     * @param baseUrl the Anthropic API base URL (no trailing slash)
     * @param apiKey the API key
     */
    public AnthropicHttpClient(HttpClient httpClient, String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    /**
     * Build an {@link HttpRequest} for the Messages API.
     *
     * @param jsonBody the serialized JSON request body
     * @return the built HTTP request
     */
    public HttpRequest buildHttpRequest(String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * Send a non-streaming request and dispatch by status code.
     *
     * @param jsonBody the serialized JSON request body
     * @return a Mono that emits the raw response body on success
     */
    public Mono<String> sendRequest(String jsonBody) {
        HttpRequest request = buildHttpRequest(jsonBody);
        return Mono.fromFuture(
                        () -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .flatMap(this::dispatchResponse);
    }

    /**
     * Send a streaming SSE request. The subscriber receives raw lines from the SSE stream.
     *
     * @param jsonBody the serialized JSON request body
     * @param lineSubscriber the Flow.Subscriber that processes SSE lines
     * @return a CompletableFuture for the HTTP response
     */
    public CompletableFuture<HttpResponse<Void>> sendStreamingRequest(
            String jsonBody, Flow.Subscriber<String> lineSubscriber) {
        HttpRequest request = buildHttpRequest(jsonBody);
        return httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.fromLineSubscriber(lineSubscriber));
    }

    /**
     * Dispatch a response based on status code.
     *
     * @param response the HTTP response
     * @return a Mono that emits the body or an error
     */
    private Mono<String> dispatchResponse(HttpResponse<String> response) {
        if (response.statusCode() == 429) {
            String retryAfter = response.headers().firstValue("retry-after").orElse(null);
            return Mono.error(
                    new ModelProviderException.RateLimitException(
                            "Anthropic API rate limited (429)",
                            ModelProviderUtils.parseRetryAfter(retryAfter)));
        }
        if (response.statusCode() >= 500) {
            return Mono.error(
                    new ModelProviderException.ApiException(
                            "Anthropic API server error: HTTP "
                                    + response.statusCode()
                                    + " - "
                                    + response.body()));
        }
        if (response.statusCode() != 200) {
            return Mono.error(
                    new ModelProviderException.ApiException(
                            "Anthropic API error: HTTP "
                                    + response.statusCode()
                                    + " - "
                                    + response.body()));
        }
        return Mono.just(response.body());
    }

    /** Return the default request/response timeout. */
    public static Duration defaultTimeout() {
        return DEFAULT_TIMEOUT;
    }
}
