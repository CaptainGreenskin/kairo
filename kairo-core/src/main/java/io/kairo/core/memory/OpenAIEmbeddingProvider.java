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
package io.kairo.core.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.memory.EmbeddingProvider;
import io.kairo.core.model.ModelProviderUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * {@link EmbeddingProvider} implementation backed by the OpenAI Embeddings API.
 *
 * <p>Uses Java {@link HttpClient} (same transport as {@code OpenAIProvider}) and Jackson for JSON
 * serialization. The blocking HTTP call is offloaded to {@link Schedulers#boundedElastic()}.
 *
 * <p>Supports OpenAI and all compatible APIs (Azure OpenAI, etc.) through a configurable base URL.
 * Batch embedding is supported — OpenAI accepts up to 2048 inputs per request.
 */
public class OpenAIEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAIEmbeddingProvider.class);
    private static final String DEFAULT_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_DIMENSIONS = 1536;
    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Create an OpenAIEmbeddingProvider with default settings.
     *
     * @param apiKey the OpenAI API key
     */
    public OpenAIEmbeddingProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_MODEL);
    }

    /**
     * Create an OpenAIEmbeddingProvider with a custom base URL and model.
     *
     * @param apiKey the API key for authentication
     * @param baseUrl the base URL of the API provider (must not end with {@code /v1})
     * @param model the embedding model name
     */
    public OpenAIEmbeddingProvider(String apiKey, String baseUrl, String model) {
        this(
                apiKey,
                baseUrl,
                model,
                ModelProviderUtils.createHttpClient(DEFAULT_TIMEOUT),
                ModelProviderUtils.createObjectMapper());
    }

    /**
     * Create an OpenAIEmbeddingProvider with full customization.
     *
     * @param apiKey the API key
     * @param baseUrl the base URL
     * @param model the embedding model name
     * @param httpClient the HTTP client
     * @param objectMapper the Jackson ObjectMapper
     */
    OpenAIEmbeddingProvider(
            String apiKey,
            String baseUrl,
            String model,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.baseUrl =
                baseUrl != null
                        ? (baseUrl.endsWith("/")
                                ? baseUrl.substring(0, baseUrl.length() - 1)
                                : baseUrl)
                        : DEFAULT_BASE_URL;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Mono<float[]> embed(String text) {
        return embedAll(List.of(text)).next();
    }

    @Override
    public Flux<float[]> embedAll(List<String> texts) {
        return Mono.fromCallable(
                        () -> {
                            String jsonBody = buildRequestBody(texts);
                            HttpRequest request = buildHttpRequest(jsonBody);

                            log.debug(
                                    "Sending embedding request for {} text(s) to {}",
                                    texts.size(),
                                    baseUrl);

                            HttpResponse<String> response =
                                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                            if (response.statusCode() != 200) {
                                throw new RuntimeException(
                                        "OpenAI Embedding API error: HTTP "
                                                + response.statusCode()
                                                + " - "
                                                + ModelProviderUtils.sanitizeForLogging(
                                                        response.body()));
                            }

                            return parseEmbeddings(response.body());
                        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public int dimensions() {
        return DEFAULT_DIMENSIONS;
    }

    // ---- Internal helpers ----

    String buildRequestBody(List<String> texts) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("encoding_format", "float");

        if (texts.size() == 1) {
            body.put("input", texts.get(0));
        } else {
            ArrayNode inputArray = body.putArray("input");
            texts.forEach(inputArray::add);
        }

        return objectMapper.writeValueAsString(body);
    }

    private HttpRequest buildHttpRequest(String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/embeddings"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(DEFAULT_TIMEOUT)
                .build();
    }

    List<float[]> parseEmbeddings(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.get("data");

        if (data == null || !data.isArray()) {
            throw new RuntimeException("Invalid embedding response: missing 'data' array");
        }

        List<float[]> embeddings = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embeddingNode = item.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException(
                        "Invalid embedding response: missing 'embedding' array in data item");
            }
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).doubleValue();
            }
            embeddings.add(vector);
        }
        return embeddings;
    }
}
