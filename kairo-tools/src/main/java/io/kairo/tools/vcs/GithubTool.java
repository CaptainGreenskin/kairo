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
package io.kairo.tools.vcs;

import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Tool for GitHub REST API operations: issues, pull requests, and comments.
 *
 * <p>Uses the JDK {@link HttpClient} — no third-party HTTP library required. The GitHub token is
 * resolved from (in priority order):
 *
 * <ol>
 *   <li>{@code ToolContext} attribute {@code "GITHUB_TOKEN"}
 *   <li>Environment variable {@code GITHUB_TOKEN}
 * </ol>
 *
 * <p>When no token is available the tool returns an error message rather than throwing.
 *
 * @since v1.2
 */
@Tool(
        name = "github",
        description =
                "Interact with GitHub: create/list issues and PRs, add comments. Requires GITHUB_TOKEN.",
        sideEffect = ToolSideEffect.WRITE)
public final class GithubTool implements SyncTool {

    private static final Logger log = LoggerFactory.getLogger(GithubTool.class);
    private static final String API_BASE = "https://api.github.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;

    public GithubTool() {
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    GithubTool(HttpClient http) {
        this.http = http;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx));
    }

    private ToolResult doExecute(Map<String, Object> input, ToolContext context) throws Exception {
        String token = resolveToken(context);
        if (token == null || token.isBlank()) {
            return error(null, "GITHUB_TOKEN not set. Set the env var or inject via ToolContext.");
        }

        String action = required(input, "action");
        String owner = required(input, "owner");
        String repo = required(input, "repo");

        return switch (action) {
            case "create_issue" -> createIssue(token, owner, repo, input);
            case "list_issues" -> listIssues(token, owner, repo, input);
            case "create_pr" -> createPr(token, owner, repo, input);
            case "list_prs" -> listPrs(token, owner, repo, input);
            case "get_pr" -> getPr(token, owner, repo, input);
            case "add_comment" -> addComment(token, owner, repo, input);
            default ->
                    error(
                            null,
                            "Unknown action: "
                                    + action
                                    + ". Valid: create_issue, list_issues, create_pr, list_prs, get_pr, add_comment");
        };
    }

    // ---- Actions ----

    private ToolResult createIssue(
            String token, String owner, String repo, Map<String, Object> input) throws Exception {
        String title = required(input, "title");
        String body = (String) input.getOrDefault("body", "");
        String payload = json("title", title, "body", body);
        return post(token, "/repos/" + owner + "/" + repo + "/issues", payload);
    }

    private ToolResult listIssues(
            String token, String owner, String repo, Map<String, Object> input) throws Exception {
        String state = (String) input.getOrDefault("state", "open");
        int limit = intParam(input, "limit", 20);
        String url =
                "/repos/" + owner + "/" + repo + "/issues?state=" + state + "&per_page=" + limit;
        return get(token, url);
    }

    private ToolResult createPr(String token, String owner, String repo, Map<String, Object> input)
            throws Exception {
        String title = required(input, "title");
        String head = required(input, "head");
        String base = (String) input.getOrDefault("base", "main");
        String body = (String) input.getOrDefault("body", "");
        String payload = json("title", title, "head", head, "base", base, "body", body);
        return post(token, "/repos/" + owner + "/" + repo + "/pulls", payload);
    }

    private ToolResult listPrs(String token, String owner, String repo, Map<String, Object> input)
            throws Exception {
        String state = (String) input.getOrDefault("state", "open");
        int limit = intParam(input, "limit", 20);
        String url =
                "/repos/" + owner + "/" + repo + "/pulls?state=" + state + "&per_page=" + limit;
        return get(token, url);
    }

    private ToolResult getPr(String token, String owner, String repo, Map<String, Object> input)
            throws Exception {
        int number = intParam(input, "issue_number", -1);
        if (number < 0) return error(null, "'issue_number' is required for get_pr");
        return get(token, "/repos/" + owner + "/" + repo + "/pulls/" + number);
    }

    private ToolResult addComment(
            String token, String owner, String repo, Map<String, Object> input) throws Exception {
        int number = intParam(input, "issue_number", -1);
        if (number < 0) return error(null, "'issue_number' is required for add_comment");
        String body = required(input, "body");
        String payload = "{\"body\":" + jsonString(body) + "}";
        return post(
                token, "/repos/" + owner + "/" + repo + "/issues/" + number + "/comments", payload);
    }

    // ---- HTTP helpers ----

    private ToolResult get(String token, String path) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + path))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .timeout(TIMEOUT)
                        .GET()
                        .build();
        return send(req);
    }

    private ToolResult post(String token, String path, String jsonBody) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + path))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("Content-Type", "application/json")
                        .timeout(TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();
        return send(req);
    }

    private ToolResult send(HttpRequest req) throws Exception {
        log.debug("GitHub API: {} {}", req.method(), req.uri());
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        int status = resp.statusCode();
        String body = resp.body();
        if (status >= 400) {
            log.warn("GitHub API error {}: {}", status, body);
            return error(null, "GitHub API returned HTTP " + status + ": " + body);
        }
        return ok(body);
    }

    // ---- Helpers ----

    private static String resolveToken(ToolContext ctx) {
        if (ctx != null) {
            Object attr = ctx.dependencies().get("GITHUB_TOKEN");
            if (attr instanceof String s && !s.isBlank()) return s;
        }
        return System.getenv("GITHUB_TOKEN");
    }

    private static String required(Map<String, Object> input, String key) {
        Object v = input.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        return v.toString();
    }

    private static int intParam(Map<String, Object> input, String key, int defaultValue) {
        Object v = input.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Build a flat JSON object from alternating key/value pairs. */
    private static String json(String... kvPairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kvPairs.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append(jsonString(kvPairs[i])).append(':').append(jsonString(kvPairs[i + 1]));
        }
        return sb.append('}').toString();
    }

    private static String jsonString(String value) {
        if (value == null) return "null";
        return '"'
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                + '"';
    }

    private static ToolResult ok(String content) {
        return ToolResult.success(null, content);
    }

    private static ToolResult error(String id, String message) {
        return ToolResult.of(id, message, true, Map.of());
    }
}
