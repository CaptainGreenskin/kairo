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
package io.kairo.tools.openapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP tool executor for OpenAPI-registered endpoints.
 *
 * <p>Dispatches HTTP requests based on the registered method, path template, and parameters.
 * Path parameters are substituted into the URL template, query parameters are appended, and
 * request body is sent as JSON for non-GET methods.
 */
public final class OpenApiHttpTool {

    private static final Logger log = LoggerFactory.getLogger(OpenApiHttpTool.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final String baseUrl;

    /**
     * Create a tool executor targeting the given base URL.
     *
     * @param baseUrl the base URL of the API (e.g., "https://api.example.com")
     */
    public OpenApiHttpTool(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    /** Visible for testing. */
    OpenApiHttpTool(String baseUrl, HttpClient httpClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
    }

    /**
     * Execute an HTTP call based on the OpenAPI tool registration.
     *
     * @param method      HTTP method (GET, POST, etc.)
     * @param pathTemplate path template with {param} placeholders
     * @param parameters   merged parameters (path, query, body fields)
     * @param pathParams   names of path parameters (for substitution)
     * @param queryParams  names of query parameters (appended to URL)
     * @return the HTTP response body as a string
     */
    public String execute(
            String method,
            String pathTemplate,
            Map<String, Object> parameters,
            java.util.Set<String> pathParams,
            java.util.Set<String> queryParams) {

        String resolvedPath = pathTemplate;
        for (String pp : pathParams) {
            Object value = parameters.get(pp);
            if (value != null) {
                resolvedPath = resolvedPath.replace("{" + pp + "}", value.toString());
            }
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl).append(resolvedPath);
        boolean first = true;
        for (String qp : queryParams) {
            Object value = parameters.get(qp);
            if (value != null) {
                urlBuilder.append(first ? '?' : '&');
                urlBuilder.append(qp).append('=').append(value);
                first = false;
            }
        }

        String url = urlBuilder.toString();
        log.debug("OpenAPI HTTP {} {}", method, url);

        HttpRequest.Builder reqBuilder =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(DEFAULT_TIMEOUT)
                        .header("Accept", "application/json");

        String upperMethod = method.toUpperCase();
        switch (upperMethod) {
            case "GET":
                reqBuilder.GET();
                break;
            case "DELETE":
                reqBuilder.DELETE();
                break;
            case "HEAD":
                reqBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                break;
            case "OPTIONS":
                reqBuilder.method("OPTIONS", HttpRequest.BodyPublishers.noBody());
                break;
            default:
                // POST, PUT, PATCH — send body JSON
                String bodyJson = buildBodyJson(parameters, pathParams, queryParams);
                reqBuilder
                        .header("Content-Type", "application/json")
                        .method(upperMethod, HttpRequest.BodyPublishers.ofString(bodyJson));
                break;
        }

        try {
            HttpResponse<String> response =
                    httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "HTTP request failed: " + e.getMessage();
            log.error(errorMsg, e);
            return "{\"error\": \"" + errorMsg.replace("\"", "\\\"") + "\"}";
        }
    }

    /** Build a JSON string from body parameters (everything not a path/query param). */
    private static String buildBodyJson(
            Map<String, Object> parameters,
            java.util.Set<String> pathParams,
            java.util.Set<String> queryParams) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (pathParams.contains(entry.getKey()) || queryParams.contains(entry.getKey())) {
                continue;
            }
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            appendJsonValue(sb, entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
        }
    }
}
