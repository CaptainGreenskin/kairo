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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fluent builder for creating MCP async clients that wraps the official MCP Java SDK.
 *
 * <p>Supports three transport types: Stdio, Streamable HTTP, and SSE.
 *
 * <p>Example:
 *
 * <pre>{@code
 * McpAsyncClient client = McpClientBuilder.create("weather")
 *     .stdioTransport("npx", "-y", "@modelcontextprotocol/server-weather")
 *     .requestTimeout(Duration.ofSeconds(60))
 *     .build();
 * }</pre>
 */
public class McpClientBuilder {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_INIT_TIMEOUT = Duration.ofSeconds(30);
    private static final String CLIENT_NAME = "kairo-mcp";
    private static final String CLIENT_VERSION = "0.1.0";

    private final String name;
    private TransportType activeTransport;
    private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    private Duration initTimeout = DEFAULT_INIT_TIMEOUT;

    // Stdio fields
    private String stdioCommand;
    private List<String> stdioArgs = new ArrayList<>();
    private Map<String, String> stdioEnv = new HashMap<>();

    // HTTP fields
    private String httpUrl;
    private final Map<String, String> httpHeaders = new HashMap<>();
    private final Map<String, String> httpQueryParams = new HashMap<>();

    // Elicitation handler
    private ElicitationHandler elicitationHandler;

    private enum TransportType {
        STDIO,
        STREAMABLE_HTTP,
        SSE
    }

    private McpClientBuilder(String name) {
        this.name = name;
    }

    /**
     * Creates a new builder for the given MCP server name.
     *
     * @param name a unique identifier for this MCP client
     * @return a new builder instance
     */
    public static McpClientBuilder create(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MCP client name must not be null or blank");
        }
        return new McpClientBuilder(name);
    }

    /**
     * Creates a builder pre-configured from a {@link McpServerConfig}.
     *
     * @param config the server configuration
     * @return a new builder instance
     */
    public static McpClientBuilder fromConfig(McpServerConfig config) {
        McpClientBuilder builder = create(config.name());
        builder.requestTimeout = config.requestTimeout();

        // Apply bearer token first, then explicit headers (explicit headers take precedence)
        if (config.bearerToken() != null && !config.bearerToken().isBlank()) {
            builder.httpHeaders.put("Authorization", "Bearer " + config.bearerToken());
        }

        switch (config.transportType()) {
            case STDIO -> {
                if (config.command() == null || config.command().isEmpty()) {
                    throw new IllegalArgumentException("Stdio transport requires a command");
                }
                builder.stdioCommand = config.command().get(0);
                if (config.command().size() > 1) {
                    builder.stdioArgs =
                            new ArrayList<>(config.command().subList(1, config.command().size()));
                }
                if (config.env() != null) {
                    builder.stdioEnv = new HashMap<>(config.env());
                }
                builder.activeTransport = TransportType.STDIO;
            }
            case STREAMABLE_HTTP -> {
                applyHttpTransportConfig(builder, config);
                builder.activeTransport = TransportType.STREAMABLE_HTTP;
            }
            case SSE -> {
                applyHttpTransportConfig(builder, config);
                builder.activeTransport = TransportType.SSE;
            }
        }
        return builder;
    }

    private static void applyHttpTransportConfig(McpClientBuilder builder, McpServerConfig config) {
        builder.httpUrl = config.url();
        if (config.headers() != null) {
            builder.httpHeaders.putAll(config.headers());
        }
        if (config.queryParams() != null) {
            builder.httpQueryParams.putAll(config.queryParams());
        }
    }

    /** Configures Stdio transport. */
    public McpClientBuilder stdioTransport(String command, String... args) {
        this.activeTransport = TransportType.STDIO;
        this.stdioCommand = command;
        this.stdioArgs = new ArrayList<>(List.of(args));
        return this;
    }

    /** Configures Streamable HTTP transport. */
    public McpClientBuilder streamableHttpTransport(String url) {
        this.activeTransport = TransportType.STREAMABLE_HTTP;
        this.httpUrl = url;
        return this;
    }

    /** Configures SSE transport. */
    public McpClientBuilder sseTransport(String url) {
        this.activeTransport = TransportType.SSE;
        this.httpUrl = url;
        return this;
    }

    /** Adds an HTTP header (only applicable for HTTP/SSE transports). */
    public McpClientBuilder header(String key, String value) {
        this.httpHeaders.put(key, value);
        return this;
    }

    /** Adds a query parameter (only applicable for HTTP/SSE transports). */
    public McpClientBuilder queryParam(String key, String value) {
        this.httpQueryParams.put(key, value);
        return this;
    }

    /** Sets a bearer token as an {@code Authorization: Bearer <token>} header. */
    public McpClientBuilder bearerToken(String token) {
        this.httpHeaders.put("Authorization", "Bearer " + token);
        return this;
    }

    /** Adds an environment variable (only applicable for Stdio transport). */
    public McpClientBuilder env(String key, String value) {
        this.stdioEnv.put(key, value);
        return this;
    }

    /** Sets the per-request timeout. */
    public McpClientBuilder requestTimeout(Duration timeout) {
        this.requestTimeout = timeout;
        return this;
    }

    /** Sets the initialization timeout. */
    public McpClientBuilder initTimeout(Duration timeout) {
        this.initTimeout = timeout;
        return this;
    }

    /**
     * Sets the elicitation handler for MCP server elicitation requests.
     *
     * <p>If not set, an {@link AutoDeclineElicitationHandler} is used by default.
     *
     * @param handler the elicitation handler
     * @return this builder
     */
    public McpClientBuilder onElicitation(ElicitationHandler handler) {
        this.elicitationHandler = handler;
        return this;
    }

    /**
     * Sets the elicitation policy for MCP server elicitation requests.
     *
     * <p>Alias for {@link #onElicitation(ElicitationHandler)}. If not set, an {@link
     * AutoDeclineElicitationHandler} is used by default (safe for CI/server environments).
     *
     * <p>To opt-in to auto-approve (development/testing only):
     *
     * <pre>{@code
     * builder.elicitationPolicy(new DevOnlyAutoApproveHandler());
     * }</pre>
     *
     * @param handler the elicitation handler
     * @return this builder
     */
    public McpClientBuilder elicitationPolicy(ElicitationHandler handler) {
        return onElicitation(handler);
    }

    /**
     * Builds a blocking synchronous client with the default timeout of 30 seconds.
     *
     * <p>The returned {@link McpSyncClient} wraps the async client and calls {@code .block()} on
     * every method. Do NOT use in reactive pipelines.
     *
     * @return a synchronous MCP client
     */
    public McpSyncClient buildSync() {
        return new McpSyncClient(build());
    }

    /**
     * Builds a blocking synchronous client with a custom timeout.
     *
     * @param timeout the maximum duration to block on each call
     * @return a synchronous MCP client
     */
    public McpSyncClient buildSync(Duration timeout) {
        return new McpSyncClient(build(), timeout);
    }

    /**
     * Builds and returns the {@link McpAsyncClient} (not yet initialized).
     *
     * @return the MCP async client
     */
    public McpAsyncClient build() {
        if (activeTransport == null) {
            throw new IllegalStateException("Transport must be configured before building");
        }
        McpClientTransport transport = createTransport();
        McpSchema.Implementation clientInfo =
                new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION);

        // Use security-first default if no handler is set
        ElicitationHandler effectiveHandler =
                elicitationHandler != null
                        ? elicitationHandler
                        : new AutoDeclineElicitationHandler();

        // Adapt Kairo ElicitationHandler to MCP SDK Function
        Function<McpSchema.ElicitRequest, reactor.core.publisher.Mono<McpSchema.ElicitResult>>
                sdkHandler =
                        sdkRequest -> {
                            ElicitationRequest kairoRequest =
                                    new ElicitationRequest(
                                            sdkRequest.message(), sdkRequest.requestedSchema());
                            return effectiveHandler
                                    .handle(kairoRequest)
                                    .map(
                                            kairoResponse -> {
                                                McpSchema.ElicitResult.Action sdkAction =
                                                        switch (kairoResponse.action()) {
                                                            case ACCEPT ->
                                                                    McpSchema.ElicitResult.Action
                                                                            .ACCEPT;
                                                            case DECLINE ->
                                                                    McpSchema.ElicitResult.Action
                                                                            .DECLINE;
                                                            case CANCEL ->
                                                                    McpSchema.ElicitResult.Action
                                                                            .CANCEL;
                                                        };
                                                return new McpSchema.ElicitResult(
                                                        sdkAction, kairoResponse.data());
                                            });
                        };

        return McpClient.async(transport)
                .requestTimeout(requestTimeout)
                .initializationTimeout(initTimeout)
                .clientInfo(clientInfo)
                .elicitation(sdkHandler)
                .build();
    }

    private McpClientTransport createTransport() {
        return switch (activeTransport) {
            case STDIO -> createStdioTransport();
            case STREAMABLE_HTTP -> createStreamableHttpTransport();
            case SSE -> createSseTransport();
        };
    }

    private McpClientTransport createStdioTransport() {
        ServerParameters.Builder paramsBuilder = ServerParameters.builder(stdioCommand);
        if (!stdioArgs.isEmpty()) {
            paramsBuilder.args(stdioArgs);
        }
        if (!stdioEnv.isEmpty()) {
            paramsBuilder.env(stdioEnv);
        }
        return new StdioClientTransport(
                paramsBuilder.build(), new JacksonMcpJsonMapper(new ObjectMapper()));
    }

    private McpClientTransport createStreamableHttpTransport() {
        String resolvedUrl = resolvedHttpUrl();
        HttpClientStreamableHttpTransport.Builder builder =
                HttpClientStreamableHttpTransport.builder(resolvedUrl);
        if (!httpHeaders.isEmpty()) {
            builder.customizeRequest(requestCustomizer());
        }
        return builder.build();
    }

    private McpClientTransport createSseTransport() {
        String resolvedUrl = resolvedHttpUrl();
        HttpClientSseClientTransport.Builder builder =
                HttpClientSseClientTransport.builder(resolvedUrl);
        if (!httpHeaders.isEmpty()) {
            builder.customizeRequest(requestCustomizer());
        }
        return builder.build();
    }

    private String resolvedHttpUrl() {
        return appendQueryParams(httpUrl);
    }

    private java.util.function.Consumer<java.net.http.HttpRequest.Builder> requestCustomizer() {
        return requestBuilder -> httpHeaders.forEach(requestBuilder::header);
    }

    private String appendQueryParams(String baseUrl) {
        if (httpQueryParams.isEmpty()) {
            return baseUrl;
        }
        String queryString =
                httpQueryParams.entrySet().stream()
                        .map(
                                e ->
                                        URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(
                                                        e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
        return baseUrl.contains("?") ? baseUrl + "&" + queryString : baseUrl + "?" + queryString;
    }
}
