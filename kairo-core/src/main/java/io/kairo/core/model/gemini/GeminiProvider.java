/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.model.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.ModelProviderException;
import io.kairo.core.model.ModelProviderUtils;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Google Generative Language API provider — Gemini's native {@code generateContent} endpoint (NOT
 * the OpenAI-compatibility layer at {@code /openai/v1}).
 *
 * <p>Default base URL: {@code https://generativelanguage.googleapis.com}. Default model is set by
 * {@code ModelConfig.model()}. Streaming via {@code stream()} currently falls back to a single
 * {@code call()} — proper SSE streaming for {@code streamGenerateContent?alt=sse} is a follow-up.
 *
 * <p>Tool calling supported via {@code functionDeclarations}; tool results returned via {@code
 * Content.ToolUseContent}.
 *
 * <p><strong>Auth:</strong> API key goes in the URL query string (not a header), matching the
 * official SDK behavior.
 */
public final class GeminiProvider implements ModelProvider {

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GeminiRequestBuilder requestBuilder;
    private final GeminiResponseParser responseParser;

    public GeminiProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public GeminiProvider(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, ModelProviderUtils.createHttpClient(Duration.ofSeconds(30)));
    }

    public GeminiProvider(String apiKey, String baseUrl, HttpClient httpClient) {
        ModelProviderUtils.validateApiKey(apiKey, "Gemini");
        ModelProviderUtils.validateBaseUrl(baseUrl, "Gemini");
        this.httpClient = httpClient;
        this.objectMapper = ModelProviderUtils.createObjectMapper();
        String normalized =
                baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestBuilder = new GeminiRequestBuilder(apiKey, normalized, objectMapper);
        this.responseParser = new GeminiResponseParser(objectMapper);
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
        String model = config.model();
        if (model == null || model.isBlank()) {
            return Mono.error(
                    new IllegalArgumentException(
                            "GeminiProvider requires ModelConfig.model() to be set"));
        }
        return Mono.fromCallable(() -> requestBuilder.buildBody(messages, config))
                .flatMap(
                        body -> {
                            HttpRequest req = requestBuilder.buildRequest(model, body, false);
                            return Mono.fromFuture(
                                    () ->
                                            httpClient.sendAsync(
                                                    req, HttpResponse.BodyHandlers.ofString()));
                        })
                .flatMap(
                        resp -> {
                            int status = resp.statusCode();
                            String body = resp.body();
                            if (status == 429) {
                                String retryAfter =
                                        resp.headers().firstValue("retry-after").orElse(null);
                                return Mono.error(
                                        new ModelProviderException.RateLimitException(
                                                "Gemini API rate limited (429)",
                                                ModelProviderUtils.parseRetryAfter(retryAfter)));
                            }
                            if (status >= 400) {
                                return Mono.error(
                                        new ModelProviderException.ApiException(
                                                "Gemini API error " + status + ": " + body));
                            }
                            try {
                                return Mono.just(responseParser.parse(body, model));
                            } catch (Exception e) {
                                return Mono.error(
                                        new ModelProviderException.ApiException(
                                                "Failed to parse Gemini response: "
                                                        + e.getMessage(),
                                                e));
                            }
                        });
    }

    /**
     * Streaming not yet implemented — falls back to a single {@code call()} that emits one {@link
     * ModelResponse}. Follow-up will hook the {@code streamGenerateContent?alt=sse} endpoint into a
     * real SSE subscriber.
     */
    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
        return call(messages, config).flux();
    }
}
