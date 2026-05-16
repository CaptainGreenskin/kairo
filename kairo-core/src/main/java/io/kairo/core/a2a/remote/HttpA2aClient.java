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
import io.kairo.api.message.Msg;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * HTTP-based {@link A2aClient} that sends A2A requests to a remote Kairo instance.
 *
 * <p>Uses Java's built-in {@link HttpClient} (no external dependency). Requests are serialized via
 * {@link A2aMessageCodec} and sent as JSON POST to the configured endpoint.
 *
 * <p>Authentication is via Bearer token in the Authorization header. The endpoint URL pattern is:
 * {@code {baseUrl}/a2a/send} for request-response and {@code {baseUrl}/a2a/stream} for streaming.
 */
public final class HttpA2aClient implements A2aClient {

    private static final Logger log = LoggerFactory.getLogger(HttpA2aClient.class);
    private static final String SEND_PATH = "/a2a/send";
    private static final String STREAM_PATH = "/a2a/stream";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final String baseUrl;
    private final @Nullable String bearerToken;
    private final HttpClient httpClient;
    private final Duration timeout;

    private HttpA2aClient(Builder builder) {
        this.baseUrl =
                builder.baseUrl.endsWith("/")
                        ? builder.baseUrl.substring(0, builder.baseUrl.length() - 1)
                        : builder.baseUrl;
        this.bearerToken = builder.bearerToken;
        this.timeout = builder.timeout;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Mono<Msg> send(String targetAgentId, Msg message) {
        return Mono.fromCallable(
                        () -> {
                            A2aRequest request = new A2aRequest(targetAgentId, message, false);
                            String responseBody = doPost(SEND_PATH, request.toJson());
                            A2aResponse response = A2aResponse.fromJson(responseBody);

                            if (!response.isSuccess()) {
                                throw new A2aException(
                                        targetAgentId,
                                        "Remote agent error ["
                                                + response.errorCode()
                                                + "]: "
                                                + response.errorMessage());
                            }
                            if (response.message() == null) {
                                throw new A2aException(
                                        targetAgentId, "Remote agent returned no message");
                            }
                            return response.message();
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Msg> stream(String targetAgentId, Msg message) {
        return Flux.defer(
                        () -> {
                            A2aRequest request = new A2aRequest(targetAgentId, message, true);
                            String responseBody;
                            try {
                                responseBody = doPost(STREAM_PATH, request.toJson());
                            } catch (Exception e) {
                                return Flux.error(
                                        new A2aException(
                                                targetAgentId,
                                                "Remote stream failed: " + e.getMessage(),
                                                e));
                            }

                            // Parse SSE-style newline-delimited JSON responses
                            String[] lines = responseBody.split("\n");
                            return Flux.fromArray(lines)
                                    .filter(line -> !line.isBlank() && !line.startsWith(":"))
                                    .map(
                                            line -> {
                                                String data = line;
                                                if (data.startsWith("data:")) {
                                                    data = data.substring(5).trim();
                                                }
                                                A2aResponse resp = A2aResponse.fromJson(data);
                                                if (!resp.isSuccess()) {
                                                    throw new A2aException(
                                                            targetAgentId,
                                                            "Remote stream error: "
                                                                    + resp.errorMessage());
                                                }
                                                return resp.message();
                                            })
                                    .filter(Objects::nonNull);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String doPost(String path, String jsonBody) {
        URI uri = URI.create(baseUrl + path);
        HttpRequest.Builder reqBuilder =
                HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (bearerToken != null) {
            reqBuilder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpRequest httpRequest = reqBuilder.build();

        try {
            log.debug("A2A HTTP POST {}", uri);
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new A2aException(
                        "remote", "Authentication failed: HTTP " + response.statusCode());
            }
            if (response.statusCode() >= 400) {
                throw new A2aException(
                        "remote",
                        "Remote agent returned HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body());
            }
            return response.body();
        } catch (A2aException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new A2aException("remote", "HTTP request interrupted", e);
        } catch (Exception e) {
            throw new A2aException("remote", "HTTP request failed: " + e.getMessage(), e);
        }
    }

    /** Returns the base URL this client connects to. */
    public String baseUrl() {
        return baseUrl;
    }

    public static final class Builder {
        private String baseUrl;
        private @Nullable String bearerToken;
        private Duration timeout = DEFAULT_TIMEOUT;

        private Builder() {}

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            return this;
        }

        public Builder bearerToken(@Nullable String bearerToken) {
            this.bearerToken = bearerToken;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
            return this;
        }

        public HttpA2aClient build() {
            Objects.requireNonNull(baseUrl, "baseUrl must be set");
            return new HttpA2aClient(this);
        }
    }
}
