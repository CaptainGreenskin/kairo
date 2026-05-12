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
package io.kairo.tools.info;

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Performs arbitrary HTTP requests using the built-in {@link HttpClient}.
 *
 * <p>Supports common HTTP methods, custom headers, request body, configurable timeout and redirect
 * following. Private/local addresses are blocked to prevent SSRF.
 */
@Tool(
        name = "http_request",
        description =
                "Perform an arbitrary HTTP request. Supports GET, POST, PUT, DELETE, PATCH, HEAD with"
                        + " custom headers and body. Response body is limited to 100KB.",
        category = ToolCategory.INFORMATION,
        sideEffect = ToolSideEffect.WRITE)
public class HttpTool implements SyncTool {

    static final int DEFAULT_TIMEOUT_SECONDS = 30;
    static final int MAX_BYTES = 100_000;

    private static final String USER_AGENT = "Kairo-Agent/1.x";
    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD");

    private final boolean allowLocalhost;

    /** Default constructor for production use — SSRF protection is enabled. */
    public HttpTool() {
        this(false);
    }

    /**
     * Constructor allowing tests to override SSRF protection.
     *
     * @param allowLocalhost set {@code true} only in tests to allow localhost connections
     */
    HttpTool(boolean allowLocalhost) {
        this.allowLocalhost = allowLocalhost;
    }

    @ToolParam(
            description = "The request URL (must start with http:// or https://)",
            required = true)
    private String url;

    @ToolParam(description = "HTTP method: GET, POST, PUT, DELETE, PATCH, HEAD (default: GET)")
    private String method;

    @ToolParam(description = "Extra request headers as a JSON object (optional)")
    private Map<String, Object> headers;

    @ToolParam(description = "Request body string (optional)")
    private String body;

    @ToolParam(description = "Request timeout in seconds (default: 30)")
    private Integer timeoutSeconds;

    @ToolParam(description = "Whether to follow HTTP redirects (default: true)")
    private Boolean followRedirects;

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args));
    }

    @SuppressWarnings("unchecked")
    private ToolResult doExecute(Map<String, Object> input) {
        String rawUrl = (String) input.get("url");
        if (rawUrl == null || rawUrl.isBlank()) {
            return error("Parameter 'url' is required");
        }

        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            return error("URL must start with http:// or https://");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            return error("Invalid URL: " + e.getMessage());
        }

        if (!allowLocalhost && SsrfGuard.isPrivateHost(uri.getHost())) {
            return error("SSRF protection: access to private/local addresses is not allowed");
        }

        String method = parseMethod(input.get("method"));
        int timeout = parseIntOrDefault(input.get("timeoutSeconds"), DEFAULT_TIMEOUT_SECONDS);
        boolean followRedirects = parseBooleanOrDefault(input.get("followRedirects"), true);

        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeout))
                        .followRedirects(
                                followRedirects
                                        ? HttpClient.Redirect.NORMAL
                                        : HttpClient.Redirect.NEVER)
                        .build();

        try {
            HttpRequest.Builder requestBuilder =
                    HttpRequest.newBuilder()
                            .uri(uri)
                            .timeout(Duration.ofSeconds(timeout))
                            .header("User-Agent", USER_AGENT);

            Map<String, Object> extraHeaders = (Map<String, Object>) input.get("headers");
            if (extraHeaders != null) {
                for (Map.Entry<String, Object> entry : extraHeaders.entrySet()) {
                    if (entry.getValue() != null) {
                        requestBuilder.header(entry.getKey(), entry.getValue().toString());
                    }
                }
            }

            String bodyStr = input.get("body") instanceof String s ? s : null;
            HttpRequest.BodyPublisher publisher =
                    bodyStr != null && !bodyStr.isEmpty()
                            ? HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8)
                            : HttpRequest.BodyPublishers.noBody();

            switch (method) {
                case "GET" -> requestBuilder.GET();
                case "HEAD" -> requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                case "POST" -> requestBuilder.POST(publisher);
                case "PUT" -> requestBuilder.PUT(publisher);
                case "DELETE" -> requestBuilder.method("DELETE", publisher);
                case "PATCH" -> requestBuilder.method("PATCH", publisher);
                default -> requestBuilder.GET();
            }

            HttpRequest request = requestBuilder.build();
            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int statusCode = response.statusCode();
            byte[] bodyBytes = response.body();
            boolean truncated = bodyBytes.length > MAX_BYTES;
            byte[] slice = truncated ? Arrays.copyOf(bodyBytes, MAX_BYTES) : bodyBytes;
            String responseBody = new String(slice, StandardCharsets.UTF_8);

            Map<String, List<String>> responseHeaders = new HashMap<>(response.headers().map());
            // Remove status-line pseudo-header if present
            responseHeaders.remove(null);

            boolean isError = statusCode < 200 || statusCode >= 300;
            Map<String, Object> meta =
                    Map.of(
                            "statusCode",
                            statusCode,
                            "method",
                            method,
                            "url",
                            rawUrl,
                            "headers",
                            responseHeaders,
                            "truncated",
                            truncated,
                            "readOnly",
                            false);
            return isError
                    ? ToolResult.error("http_request", responseBody, meta)
                    : ToolResult.success("http_request", responseBody, meta);

        } catch (java.net.http.HttpTimeoutException e) {
            return error("Request timed out after " + timeout + "s");
        } catch (Exception e) {
            return error("HTTP request failed: " + e.getMessage());
        }
    }

    private static String parseMethod(Object value) {
        if (value instanceof String s) {
            String upper = s.trim().toUpperCase();
            if (ALLOWED_METHODS.contains(upper)) {
                return upper;
            }
        }
        return "GET";
    }

    private static int parseIntOrDefault(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    private static boolean parseBooleanOrDefault(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    private ToolResult error(String msg) {
        return ToolResult.error("http_request", msg, Map.of("readOnly", false));
    }
}
