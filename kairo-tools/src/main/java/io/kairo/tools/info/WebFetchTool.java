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

import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Fetches content from a URL via HTTP GET using the built-in {@link HttpClient}.
 *
 * <p>Only text content types are supported. Private/local addresses are blocked to prevent SSRF.
 */
@Tool(
        name = "web_fetch",
        description =
                "Fetch content from a URL via HTTP GET. Returns the response body as text. Only supports text/* content types.",
        category = ToolCategory.INFORMATION,
        sideEffect = ToolSideEffect.READ_ONLY)
public class WebFetchTool implements ToolHandler {

    static final int DEFAULT_TIMEOUT_SECONDS = 30;
    static final int DEFAULT_MAX_BYTES = 512_000;

    private static final String USER_AGENT = "Kairo-Agent/1.x";

    private static final Set<String> BLOCKED_HOST_PREFIXES =
            Set.of("localhost", "127.", "192.168.", "10.", "::1");

    private static final Set<String> BLOCKED_HOST_RANGES_172 =
            Set.of(
                    "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.",
                    "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.",
                    "172.30.", "172.31.");

    private final boolean allowLocalhost;
    private final HttpClient httpClient;

    /** Default constructor for production use — SSRF protection is enabled. */
    public WebFetchTool() {
        this(false);
    }

    /**
     * Constructor allowing tests to override SSRF protection.
     *
     * @param allowLocalhost set {@code true} only in tests to allow localhost connections
     */
    WebFetchTool(boolean allowLocalhost) {
        this.allowLocalhost = allowLocalhost;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    @ToolParam(
            description = "The URL to fetch (must start with http:// or https://)",
            required = true)
    private String url;

    @ToolParam(description = "Request timeout in seconds (default: 30)")
    private Integer timeoutSeconds;

    @ToolParam(description = "Maximum response body size in bytes (default: 512000)")
    private Integer maxBytes;

    @Override
    public ToolResult execute(Map<String, Object> input) {
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

        if (!allowLocalhost && isPrivateHost(uri.getHost())) {
            return error("SSRF protection: access to private/local addresses is not allowed");
        }

        int timeout = parseIntOrDefault(input.get("timeoutSeconds"), DEFAULT_TIMEOUT_SECONDS);
        int limit = parseIntOrDefault(input.get("maxBytes"), DEFAULT_MAX_BYTES);

        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(uri)
                            .timeout(Duration.ofSeconds(timeout))
                            .header("User-Agent", USER_AGENT)
                            .GET()
                            .build();

            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            String contentType =
                    response.headers()
                            .firstValue("Content-Type")
                            .orElse("application/octet-stream");
            if (!contentType.startsWith("text/")
                    && !contentType.contains("json")
                    && !contentType.contains("xml")
                    && !contentType.contains("javascript")) {
                return error("URL returns non-text content: " + contentType);
            }

            int statusCode = response.statusCode();
            byte[] bodyBytes = response.body();
            boolean truncated = bodyBytes.length > limit;
            byte[] slice = truncated ? java.util.Arrays.copyOf(bodyBytes, limit) : bodyBytes;
            String body = new String(slice, StandardCharsets.UTF_8);
            if (truncated) {
                body += "\n... (truncated at " + limit + " bytes)";
            }

            boolean isError = statusCode >= 400;
            return new ToolResult(
                    "web_fetch",
                    body,
                    isError,
                    Map.of("statusCode", statusCode, "url", rawUrl, "contentType", contentType));

        } catch (java.net.http.HttpTimeoutException e) {
            return error("Request timed out after " + timeout + "s");
        } catch (Exception e) {
            return error("Failed to fetch URL: " + e.getMessage());
        }
    }

    private static boolean isPrivateHost(String host) {
        if (host == null) return true;
        String h = host.toLowerCase();
        for (String prefix : BLOCKED_HOST_PREFIXES) {
            if (h.equals(prefix) || h.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : BLOCKED_HOST_RANGES_172) {
            if (h.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static int parseIntOrDefault(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    private ToolResult error(String msg) {
        return new ToolResult("web_fetch", msg, true, Map.of());
    }
}
