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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.model.RawStreamingModelProvider;
import io.kairo.api.model.StreamChunk;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.core.model.ExceptionMapper;
import io.kairo.core.model.ModelProviderException;
import io.kairo.core.model.ModelProviderUtils;
import io.kairo.core.model.ProviderRetry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * {@link ModelProvider} implementation for the Anthropic Messages API.
 *
 * <p>Supports both synchronous and streaming calls to Claude models, including extended thinking,
 * tool use, and prompt caching.
 *
 * <p>This class acts as a facade, delegating to:
 *
 * <ul>
 *   <li>{@link AnthropicRequestBuilder} — request serialization
 *   <li>{@link AnthropicResponseParser} — response deserialization
 *   <li>{@link AnthropicSseSubscriber} / {@link RawAnthropicSseSubscriber} — SSE stream handling
 *   <li>{@link AnthropicErrorClassifier} — error retryability classification
 *   <li>{@link CacheBreakDetector} — KV-cache break detection
 * </ul>
 *
 * <p>Uses JDK 11+ built-in {@link HttpClient} for non-blocking HTTP calls.
 */
public class AnthropicProvider implements RawStreamingModelProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final Duration STREAM_IDLE_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final AnthropicHttpClient anthropicHttpClient;
    private final AnthropicRequestBuilder requestBuilder;
    private final AnthropicResponseParser responseParser;
    private final AnthropicErrorClassifier errorClassifier;
    private final CacheBreakDetector cacheDetector = new CacheBreakDetector();

    /**
     * Create an AnthropicProvider with default settings.
     *
     * @param apiKey the Anthropic API key
     */
    public AnthropicProvider(String apiKey) {
        this(apiKey, "https://api.anthropic.com");
    }

    /**
     * Create an AnthropicProvider with a custom base URL.
     *
     * @param apiKey the Anthropic API key
     * @param baseUrl the API base URL (e.g. for proxy)
     */
    public AnthropicProvider(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, ModelProviderUtils.createHttpClient(Duration.ofSeconds(30)));
    }

    /**
     * Create an AnthropicProvider with full customization.
     *
     * @param apiKey the Anthropic API key
     * @param baseUrl the API base URL
     * @param httpClient the HTTP client to use
     */
    public AnthropicProvider(String apiKey, String baseUrl, HttpClient httpClient) {
        ModelProviderUtils.validateApiKey(apiKey, "Anthropic");
        ModelProviderUtils.validateBaseUrl(baseUrl, "Anthropic");
        String normalizedBaseUrl =
                baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = ModelProviderUtils.createObjectMapper();
        this.anthropicHttpClient = new AnthropicHttpClient(httpClient, normalizedBaseUrl, apiKey);
        this.requestBuilder = new AnthropicRequestBuilder(this.objectMapper);
        this.responseParser = new AnthropicResponseParser(this.objectMapper);
        this.errorClassifier = new AnthropicErrorClassifier();
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
        return Mono.fromCallable(() -> requestBuilder.buildRequestBody(messages, config, false))
                .flatMap(anthropicHttpClient::sendRequest)
                .flatMap(
                        body -> {
                            try {
                                return Mono.just(responseParser.parseResponse(body));
                            } catch (Exception e) {
                                return Mono.error(
                                        new ModelProviderException.ApiException(
                                                "Failed to parse Anthropic response", e));
                            }
                        })
                .transform(
                        mono ->
                                ProviderRetry.withConfigPolicy(
                                        mono,
                                        config,
                                        "anthropic",
                                        errorClassifier::isRetryableError,
                                        CALL_TIMEOUT))
                .onErrorMap(ExceptionMapper::toApiException)
                .doOnNext(
                        response -> {
                            String sysPrompt =
                                    AnthropicRequestBuilder.resolveSystemPrompt(messages, config);
                            List<ToolDefinition> tools =
                                    config.tools() != null ? config.tools() : List.of();
                            CacheCheckResult cacheResult =
                                    cacheDetector.check(response.usage(), sysPrompt, tools);
                            if (cacheResult.isCacheBroken()) {
                                log.warn(
                                        "Cache break detected: reasons={}, hitRatio={}",
                                        cacheResult.reasons(),
                                        String.format("%.2f", cacheResult.hitRatio()));
                            }
                        });
    }

    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
        return Flux.defer(
                        () -> {
                            try {
                                String body =
                                        requestBuilder.buildRequestBody(messages, config, true);

                                Sinks.Many<ModelResponse> sink =
                                        Sinks.many().unicast().onBackpressureBuffer();

                                anthropicHttpClient
                                        .sendStreamingRequest(
                                                body,
                                                new AnthropicSseSubscriber(sink, objectMapper))
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
                                        "anthropic-stream",
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

                                Sinks.Many<StreamChunk> sink =
                                        Sinks.many().unicast().onBackpressureBuffer();

                                anthropicHttpClient
                                        .sendStreamingRequest(
                                                body,
                                                new RawAnthropicSseSubscriber(sink, objectMapper))
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
                                        "anthropic-stream-raw",
                                        errorClassifier::isRetryableError,
                                        STREAM_IDLE_TIMEOUT))
                .onErrorMap(ExceptionMapper::toApiException);
    }

    /**
     * Run cache break detection on a response and record results to a span.
     *
     * @param response the model response containing usage data
     * @param systemPrompt the system prompt sent in this call
     * @param tools the tool definitions sent in this call
     * @param span the tracing span to record cache metrics on
     * @return the cache check result
     */
    public CacheCheckResult checkCacheAndRecord(
            ModelResponse response,
            String systemPrompt,
            List<ToolDefinition> tools,
            io.kairo.api.tracing.Span span) {
        CacheCheckResult result = cacheDetector.check(response.usage(), systemPrompt, tools);
        span.setAttribute("cache.hit_ratio", result.hitRatio());
        span.setAttribute("cache.read_tokens", result.cacheReadTokens());
        span.setAttribute("cache.creation_tokens", result.cacheCreationTokens());
        if (result.isCacheBroken()) {
            span.setAttribute("cache.broken", true);
            span.setAttribute("cache.break_reasons", String.join(",", result.reasons()));
        }
        return result;
    }

    /**
     * Parse a non-streaming Anthropic API response. Delegates to {@link AnthropicResponseParser}.
     */
    public ModelResponse parseResponse(String responseBody) throws JsonProcessingException {
        return responseParser.parseResponse(responseBody);
    }

    /**
     * Build the Anthropic Messages API request body. Delegates to {@link AnthropicRequestBuilder}.
     */
    public String buildRequestBody(List<Msg> messages, ModelConfig config, boolean stream)
            throws JsonProcessingException {
        return requestBuilder.buildRequestBody(messages, config, stream);
    }
}
