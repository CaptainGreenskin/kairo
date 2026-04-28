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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Searches the web using the Tavily Search API.
 *
 * <p>Requires a {@code TAVILY_API_KEY} environment variable or tool dependency. Returns structured
 * results including an AI-generated answer and individual web results.
 */
@Tool(
        name = "web_search",
        description =
                "Search the web using Tavily Search API. Returns AI-generated answer and top web results."
                        + " Requires TAVILY_API_KEY environment variable.",
        category = ToolCategory.INFORMATION,
        sideEffect = ToolSideEffect.READ_ONLY)
public class WebSearchTool implements ToolHandler {

    private static final String TAVILY_URL = "https://api.tavily.com/search";
    private static final int TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build();

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(input, resolveApiKey(null));
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        String apiKey = (String) context.dependencies().get("TAVILY_API_KEY");
        return doExecute(input, resolveApiKey(apiKey));
    }

    private ToolResult doExecute(Map<String, Object> input, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return error(
                    "TAVILY_API_KEY is not set. Set the environment variable or inject via ToolContext.dependencies(\"TAVILY_API_KEY\")");
        }

        String query = (String) input.get("query");
        if (query == null || query.isBlank()) {
            return error("Parameter 'query' is required");
        }

        int maxResults = DEFAULT_MAX_RESULTS;
        Object maxResultsRaw = input.get("maxResults");
        if (maxResultsRaw instanceof Number n) {
            maxResults = Math.max(1, Math.min(20, n.intValue()));
        }

        boolean includeAnswer = !Boolean.FALSE.equals(input.get("includeAnswer"));

        try {
            String responseBody = callTavilyApi(apiKey, query, maxResults, includeAnswer);
            return parseResponse(responseBody, query);
        } catch (Exception e) {
            return error("Tavily API call failed: " + e.getMessage());
        }
    }

    private String callTavilyApi(String apiKey, String query, int maxResults, boolean includeAnswer)
            throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("api_key", apiKey);
        body.put("query", query);
        body.put("max_results", maxResults);
        body.put("include_answer", includeAnswer);

        String requestBody = mapper.writeValueAsString(body);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(TAVILY_URL))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw new IllegalArgumentException("Invalid TAVILY_API_KEY (401 Unauthorized)");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Tavily API returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private ToolResult parseResponse(String responseBody, String query)
            throws JsonProcessingException {
        JsonNode root = mapper.readTree(responseBody);

        StringBuilder sb = new StringBuilder();

        // AI-generated answer
        JsonNode answerNode = root.get("answer");
        if (answerNode != null && !answerNode.isNull()) {
            sb.append("**Answer**: ").append(answerNode.asText()).append("\n\n");
        }

        // Individual results
        JsonNode resultsNode = root.get("results");
        List<Map<String, String>> resultsList = new ArrayList<>();
        if (resultsNode != null && resultsNode.isArray()) {
            sb.append("**Results**:\n");
            int i = 1;
            for (JsonNode result : resultsNode) {
                String title = result.path("title").asText("");
                String url = result.path("url").asText("");
                String content = result.path("content").asText("");
                sb.append(i++)
                        .append(". **")
                        .append(title)
                        .append("**\n   ")
                        .append(url)
                        .append("\n   ")
                        .append(
                                content.length() > 200
                                        ? content.substring(0, 200) + "..."
                                        : content)
                        .append("\n\n");
                resultsList.add(Map.of("title", title, "url", url, "snippet", content));
            }
        }

        return new ToolResult(
                "web_search",
                sb.toString().trim(),
                false,
                Map.of("query", query, "resultCount", resultsList.size(), "results", resultsList));
    }

    private String resolveApiKey(String injected) {
        if (injected != null && !injected.isBlank()) return injected;
        return System.getenv("TAVILY_API_KEY");
    }

    private ToolResult error(String msg) {
        return new ToolResult("web_search", msg, true, Map.of());
    }
}
