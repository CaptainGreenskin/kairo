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
package io.kairo.core.model.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.RawStreamingModelProvider;
import io.kairo.api.model.StreamChunk;
import io.kairo.core.model.ExceptionMapper;
import io.kairo.core.model.ModelProviderException;
import io.kairo.core.model.ModelProviderUtils;
import io.kairo.core.model.ProviderRetry;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * {@link ModelProvider} implementation for the OpenAI Chat Completions API.
 *
 * <p>Supports OpenAI and all OpenAI-compatible APIs (DashScope/Qwen, Azure OpenAI, DeepSeek,
 * Together, Groq, GLM, etc.) through a configurable base URL and chat completions path.
 *
 * <p>The final request URL is constructed as {@code baseUrl + chatCompletionsPath}. See the
 * individual constructors for details on how each parameter affects URL construction.
 *
 * <p>This class acts as a facade, delegating to:
 *
 * <ul>
 *   <li>{@link OpenAIRequestBuilder} — request serialization
 *   <li>{@link OpenAIResponseParser} — response deserialization
 *   <li>{@link OpenAISseSubscriber} / {@link RawOpenAISseSubscriber} — SSE stream handling
 *   <li>{@link OpenAIErrorClassifier} — error retryability classification
 * </ul>
 *
 * @see io.kairo.spring.boot.AgentRuntimeAutoConfiguration AgentRuntimeAutoConfiguration — Spring
 *     Boot auto-configuration that creates an OpenAIProvider using the 2-arg constructor
 */
public class OpenAIProvider implements RawStreamingModelProvider {

    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAIRequestBuilder requestBuilder;
    private final OpenAIResponseParser responseParser;
    private final OpenAIErrorClassifier errorClassifier;

    // Kept for reflective access by tests and diagnostic tools
    @SuppressWarnings("unused")
    private final String baseUrl;

    @SuppressWarnings("unused")
    private final String chatCompletionsPath;

    /**
     * Create an OpenAIProvider with default settings.
     *
     * @param apiKey the OpenAI API key
     */
    public OpenAIProvider(String apiKey) {
        this(apiKey, "https://api.openai.com");
    }

    /**
     * Create an OpenAIProvider with a custom base URL for compatible APIs.
     *
     * <p>This constructor auto-appends {@code /v1/chat/completions} to the provided {@code
     * baseUrl}. Therefore, the {@code baseUrl} should <strong>not</strong> include {@code /v1} —
     * otherwise the final URL will contain a duplicated path segment (e.g. {@code
     * /v1/v1/chat/completions}).
     *
     * <p><strong>Examples:</strong>
     *
     * <pre>{@code
     * // For OpenAI:
     * new OpenAIProvider("sk-xxx", "https://api.openai.com")
     * // → calls https://api.openai.com/v1/chat/completions
     *
     * // For DashScope (Qwen):
     * new OpenAIProvider("sk-xxx", "https://dashscope.aliyuncs.com/compatible-mode")
     * // → calls https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
     *
     * // For DeepSeek:
     * new OpenAIProvider("sk-xxx", "https://api.deepseek.com")
     * // → calls https://api.deepseek.com/v1/chat/completions
     * }</pre>
     *
     * @param apiKey the API key for authentication
     * @param baseUrl the base URL of the API provider (must <em>not</em> end with {@code /v1})
     * @see #OpenAIProvider(String, String, String) for specifying a custom chat completions path
     */
    public OpenAIProvider(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, "/v1/chat/completions");
    }

    /**
     * Create an OpenAIProvider with a custom base URL and an explicit chat completions path.
     *
     * <p>Unlike the {@linkplain #OpenAIProvider(String, String) 2-arg constructor}, this
     * constructor does <strong>not</strong> auto-append {@code /v1/chat/completions}. Instead, it
     * uses the provided {@code chatCompletionsPath} as-is, which is useful when the API provider
     * does not follow the standard {@code /v1/chat/completions} convention.
     *
     * <p>The final URL is: {@code baseUrl + chatCompletionsPath}.
     *
     * <p><strong>Examples:</strong>
     *
     * <pre>{@code
     * // For DashScope with explicit path:
     * new OpenAIProvider("sk-xxx",
     *     "https://dashscope.aliyuncs.com/compatible-mode/v1", "/chat/completions")
     * // → calls https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
     *
     * // For GLM (Zhipu AI):
     * new OpenAIProvider("sk-xxx",
     *     "https://open.bigmodel.cn/api/paas/v4", "/chat/completions")
     * // → calls https://open.bigmodel.cn/api/paas/v4/chat/completions
     * }</pre>
     *
     * @param apiKey the API key for authentication
     * @param baseUrl the base URL of the API provider (may include version path segments)
     * @param chatCompletionsPath the path appended to {@code baseUrl} for chat completions (e.g.
     *     {@code "/chat/completions"})
     */
    public OpenAIProvider(String apiKey, String baseUrl, String chatCompletionsPath) {
        this(
                apiKey,
                baseUrl,
                chatCompletionsPath,
                ModelProviderUtils.createHttpClient(Duration.ofSeconds(30)));
    }

    /**
     * Create an OpenAIProvider with full customization.
     *
     * @param apiKey the API key
     * @param baseUrl the base URL
     * @param chatCompletionsPath the path for chat completions
     * @param httpClient the HTTP client
     */
    public OpenAIProvider(
            String apiKey, String baseUrl, String chatCompletionsPath, HttpClient httpClient) {
        ModelProviderUtils.validateApiKey(apiKey, "OpenAI");
        ModelProviderUtils.validateBaseUrl(baseUrl, "OpenAI");
        String normalizedBaseUrl =
                baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = ModelProviderUtils.createObjectMapper();
        this.baseUrl = normalizedBaseUrl;
        this.chatCompletionsPath = chatCompletionsPath;
        this.requestBuilder =
                new OpenAIRequestBuilder(
                        apiKey, normalizedBaseUrl, chatCompletionsPath, objectMapper);
        this.responseParser = new OpenAIResponseParser(objectMapper);
        this.errorClassifier = new OpenAIErrorClassifier();
    }

    @Override
    public String name() {
        return "openai";
    }

    /**
     * Parse a JSON response body into a {@link ModelResponse}.
     *
     * <p>Delegates to {@link OpenAIResponseParser#parseResponse(String)}.
     */
    public ModelResponse parseResponse(String responseBody) throws JsonProcessingException {
        return responseParser.parseResponse(responseBody);
    }

    /**
     * Build a JSON request body from messages and config.
     *
     * <p>Delegates to {@link OpenAIRequestBuilder#buildRequestBody(List, ModelConfig, boolean)}.
     */
    public String buildRequestBody(List<Msg> messages, ModelConfig config, boolean stream)
            throws JsonProcessingException {
        return requestBuilder.buildRequestBody(messages, config, stream);
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
        return Mono.fromCallable(() -> requestBuilder.buildRequestBody(messages, config, false))
                .flatMap(
                        body -> {
                            HttpRequest request = requestBuilder.buildHttpRequest(body);
                            return Mono.fromFuture(
                                    () ->
                                            httpClient.sendAsync(
                                                    request, HttpResponse.BodyHandlers.ofString()));
                        })
                .flatMap(
                        response -> {
                            if (response.statusCode() == 429) {
                                String retryAfter =
                                        response.headers().firstValue("retry-after").orElse(null);
                                return Mono.error(
                                        new ModelProviderException.RateLimitException(
                                                "OpenAI API rate limited (429)",
                                                ModelProviderUtils.parseRetryAfter(retryAfter)));
                            }
                            if (response.statusCode() != 200) {
                                return Mono.error(
                                        new ModelProviderException.ApiException(
                                                "OpenAI API error: HTTP "
                                                        + response.statusCode()
                                                        + " - "
                                                        + ModelProviderUtils.sanitizeForLogging(
                                                                response.body())));
                            }
                            try {
                                return Mono.just(responseParser.parseResponse(response.body()));
                            } catch (Exception e) {
                                return Mono.error(
                                        new ModelProviderException.ApiException(
                                                "Failed to parse OpenAI response", e));
                            }
                        })
                .transform(
                        mono ->
                                ProviderRetry.withConfigPolicy(
                                        mono,
                                        config,
                                        "openai",
                                        errorClassifier::isRetryableError,
                                        CALL_TIMEOUT))
                .onErrorMap(ExceptionMapper::toApiException);
    }

    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
        return Flux.defer(
                        () -> {
                            try {
                                String body =
                                        requestBuilder.buildRequestBody(messages, config, true);
                                HttpRequest request = requestBuilder.buildHttpRequest(body);

                                Sinks.Many<ModelResponse> sink =
                                        Sinks.many().unicast().onBackpressureBuffer();

                                httpClient
                                        .sendAsync(
                                                request,
                                                HttpResponse.BodyHandlers.fromLineSubscriber(
                                                        new OpenAISseSubscriber(
                                                                sink, objectMapper)))
                                        .whenComplete(
                                                (resp, err) -> {
                                                    if (err != null) {
                                                        sink.tryEmitError(
                                                                new ModelProviderException
                                                                        .ApiException(
                                                                        "Streaming request failed",
                                                                        err));
                                                    }
                                                });

                                return sink.asFlux();
                            } catch (Exception e) {
                                return Flux.error(
                                        new ModelProviderException.ApiException(
                                                "Failed to build streaming request", e));
                            }
                        })
                .transform(
                        flux ->
                                ProviderRetry.withConfigPolicy(
                                        flux,
                                        config,
                                        "openai-stream",
                                        errorClassifier::isRetryableError,
                                        STREAM_IDLE_TIMEOUT))
                .onErrorMap(ExceptionMapper::toApiException);
    }

    /**
     * Stream raw chunks for incremental processing.
     *
     * <p>Unlike {@link #stream(List, ModelConfig)} which collects the full response, this emits
     * {@link StreamChunk} objects as they arrive from the SSE stream. Consumers can use {@link
     * io.kairo.core.model.StreamingToolDetector} to detect complete tool_use blocks for early
     * execution.
     *
     * @param messages the conversation history
     * @param config model configuration
     * @return a Flux of raw streaming chunks
     */
    @Override
    public Flux<StreamChunk> streamRaw(List<Msg> messages, ModelConfig config) {
        return Flux.defer(
                        () -> {
                            try {
                                String body =
                                        requestBuilder.buildRequestBody(messages, config, true);
                                HttpRequest request = requestBuilder.buildHttpRequest(body);

                                Sinks.Many<StreamChunk> sink =
                                        Sinks.many().unicast().onBackpressureBuffer();

                                httpClient
                                        .sendAsync(
                                                request,
                                                HttpResponse.BodyHandlers.fromLineSubscriber(
                                                        new RawOpenAISseSubscriber(
                                                                sink, objectMapper)))
                                        .whenComplete(
                                                (resp, err) -> {
                                                    if (err != null) {
                                                        sink.tryEmitNext(
                                                                StreamChunk.error(
                                                                        "Streaming request failed: "
                                                                                + err
                                                                                        .getMessage()));
                                                        sink.tryEmitComplete();
                                                    }
                                                });

                                return sink.asFlux();
                            } catch (Exception e) {
                                return Flux.error(
                                        new ModelProviderException.ApiException(
                                                "Failed to build streaming request", e));
                            }
                        })
                .transform(
                        flux ->
                                ProviderRetry.withConfigPolicy(
                                        flux,
                                        config,
                                        "openai-stream-raw",
                                        errorClassifier::isRetryableError,
                                        STREAM_IDLE_TIMEOUT))
                .onErrorMap(ExceptionMapper::toApiException);
    }
}
