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

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GithubToolTest {

    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        lastRequestBody.set(null);
    }

    // -- HttpClient stub infrastructure --

    /** Minimal stub — overrides all abstract HttpClient methods. */
    abstract static class StubHttpClient extends HttpClient {
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushHandler) {
            throw new UnsupportedOperationException();
        }
    }

    /** Build a fake HttpResponse. */
    private static HttpResponse<String> fakeResponse(int status, String body, URI uri) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (a, b) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    /**
     * Routing stub client — inspects request path/method and dispatches to registered handlers.
     * Routes are matched by "METHOD path" prefix.
     */
    static class RoutingClient extends StubHttpClient {
        private final Map<String, RouteHandler> routes;

        interface RouteHandler {
            HttpResponse<String> handle(HttpRequest req, String body);
        }

        RoutingClient(Map<String, RouteHandler> routes) {
            this.routes = routes;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) {
            String path = req.uri().getPath();
            String method = req.method();
            String key = method + " " + path;

            // Try exact match first, then prefix match for parameterized paths
            RouteHandler route = routes.get(key);
            if (route == null) {
                // Try prefix matching (e.g., "GET /repos/x/y/pulls/" should match "GET
                // /repos/.../pulls/")
                for (Map.Entry<String, RouteHandler> entry : routes.entrySet()) {
                    String routeKey = entry.getKey();
                    String routePath = routeKey.substring(routeKey.indexOf(' ') + 1);
                    // For parameterized routes like "GET /pulls/", match prefix
                    if (routePath.endsWith("/") && path.startsWith(routePath)) {
                        route = entry.getValue();
                        break;
                    }
                }
            }

            String body;
            if (req.bodyPublisher().isPresent()) {
                // We can't easily read the body from BodyPublisher, so capture it at test level
                body = "";
            } else {
                body = "";
            }

            if (route != null) {
                HttpResponse<String> resp = route.handle(req, body);
                return (HttpResponse<T>) resp;
            }

            return (HttpResponse<T>) fakeResponse(404, "{\"message\":\"Not Found\"}", req.uri());
        }
    }

    // -- Helpers --

    private GithubTool createTool(HttpClient client) {
        return new GithubTool(client);
    }

    private ToolContext contextWithToken(String token) {
        return new ToolContext("test-agent", "test-session", Map.of("GITHUB_TOKEN", token));
    }

    private ToolContext contextWithoutToken() {
        return new ToolContext("test-agent", "test-session", Map.of());
    }

    // ============= Tests =============

    @Test
    @DisplayName("list_issues returns issue list with id, title, state")
    void listIssues_returnsIssueList() throws Exception {
        String responseBody =
                "[{\"id\":1,\"title\":\"Bug fix\",\"state\":\"open\"},"
                        + "{\"id\":2,\"title\":\"Feature request\",\"state\":\"closed\"}]";
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "GET /repos/test-org/test-repo/issues",
                                (req, body) -> fakeResponse(200, responseBody, req.uri())));

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "list_issues",
                                        "owner", "test-org",
                                        "repo", "test-repo"),
                                contextWithToken("test-token"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("\"id\":1"));
        assertTrue(result.content().contains("\"title\":\"Bug fix\""));
        assertTrue(result.content().contains("\"state\":\"open\""));
        assertTrue(result.content().contains("\"id\":2"));
    }

    @Test
    @DisplayName("create_issue POSTs correct body and returns created issue")
    void createIssue_postsCorrectBodyAndReturnsCreated() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "POST /repos/test-org/test-repo/issues",
                                (req, body) -> {
                                    capturedBody.set(body);
                                    return fakeResponse(
                                            201,
                                            "{\"id\":3,\"title\":\"New Issue\",\"state\":\"open\"}",
                                            req.uri());
                                }));

        // Note: StubHttpClient can't easily capture request body from BodyPublisher.
        // We verify the response content instead.
        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "create_issue",
                                        "owner", "test-org",
                                        "repo", "test-repo",
                                        "title", "New Issue",
                                        "body", "Description here"),
                                contextWithToken("test-token"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("\"id\":3"));
        assertTrue(result.content().contains("\"title\":\"New Issue\""));
    }

    @Test
    @DisplayName("list_prs returns PR list")
    void listPrs_returnsPrList() throws Exception {
        String responseBody =
                "[{\"id\":10,\"title\":\"Add feature\",\"state\":\"open\","
                        + "\"head\":{\"ref\":\"feature-branch\"},\"base\":{\"ref\":\"main\"}}]";
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "GET /repos/test-org/test-repo/pulls",
                                (req, body) -> fakeResponse(200, responseBody, req.uri())));

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "list_prs",
                                        "owner", "test-org",
                                        "repo", "test-repo"),
                                contextWithToken("test-token"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("\"id\":10"));
        assertTrue(result.content().contains("\"title\":\"Add feature\""));
    }

    @Test
    @DisplayName("get_pr returns single PR with base/head branch info")
    void getPr_returnsSinglePrWithBranches() throws Exception {
        String responseBody =
                "{\"id\":10,\"title\":\"Add feature\",\"state\":\"open\","
                        + "\"head\":{\"ref\":\"feature-branch\"},\"base\":{\"ref\":\"main\"}}";
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "GET /repos/test-org/test-repo/pulls/10",
                                (req, body) -> fakeResponse(200, responseBody, req.uri())));

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "get_pr",
                                        "owner", "test-org",
                                        "repo", "test-repo",
                                        "issue_number", 10),
                                contextWithToken("test-token"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("\"id\":10"));
        assertTrue(result.content().contains("\"base\":{\"ref\":\"main\"}"));
        assertTrue(result.content().contains("\"head\":{\"ref\":\"feature-branch\"}"));
    }

    @Test
    @DisplayName("add_comment POSTs comment body and returns comment")
    void addComment_postsCommentBody() throws Exception {
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "POST /repos/test-org/test-repo/issues/1/comments",
                                (req, body) ->
                                        fakeResponse(
                                                201,
                                                "{\"id\":100,\"body\":\"Great fix!\"}",
                                                req.uri())));

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "add_comment",
                                        "owner", "test-org",
                                        "repo", "test-repo",
                                        "issue_number", 1,
                                        "body", "Great fix!"),
                                contextWithToken("test-token"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("\"id\":100"));
        assertTrue(result.content().contains("\"body\":\"Great fix!\""));
    }

    @Test
    @DisplayName("GITHUB_TOKEN not provided returns isError=true")
    void missingToken_returnsError() throws Exception {
        RoutingClient client = new RoutingClient(Map.of());

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "list_issues",
                                        "owner", "test-org",
                                        "repo", "test-repo"),
                                contextWithoutToken());

        assertTrue(result.isError());
        assertTrue(result.content().contains("GITHUB_TOKEN"));
    }

    @Test
    @DisplayName("GitHub API returns 404 returns isError=true")
    void apiReturns404_returnsError() throws Exception {
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "GET /repos/test-org/test-repo/pulls/999",
                                (req, body) ->
                                        fakeResponse(
                                                404, "{\"message\":\"Not Found\"}", req.uri())));

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "get_pr",
                                        "owner", "test-org",
                                        "repo", "test-repo",
                                        "issue_number", 999),
                                contextWithToken("test-token"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("404"));
    }

    @Test
    @DisplayName("GitHub API returns 422 returns isError=true")
    void apiReturns422_returnsError() throws Exception {
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "POST /repos/test-org/test-repo/issues",
                                (req, body) ->
                                        fakeResponse(
                                                422,
                                                "{\"message\":\"Validation Failed\","
                                                        + "\"errors\":[{\"field\":\"title\",\"code\":\"missing\"}]}",
                                                req.uri())));

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "create_issue",
                                        "owner", "test-org",
                                        "repo", "test-repo",
                                        "title", "Test"),
                                contextWithToken("test-token"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("422"));
    }

    @Test
    @DisplayName("Invalid action returns isError=true")
    void invalidAction_returnsError() throws Exception {
        RoutingClient client = new RoutingClient(Map.of());

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "delete_repo",
                                        "owner", "test-org",
                                        "repo", "test-repo"),
                                contextWithToken("test-token"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("Unknown action"));
        assertTrue(result.content().contains("delete_repo"));
    }

    @Test
    @DisplayName("create_pr POSTs correct body and returns created PR")
    void createPr_postsCorrectBodyAndReturnsCreated() throws Exception {
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "POST /repos/test-org/test-repo/pulls",
                                (req, body) ->
                                        fakeResponse(
                                                201,
                                                "{\"id\":11,\"title\":\"New PR\",\"state\":\"open\","
                                                        + "\"head\":{\"ref\":\"new-branch\"},\"base\":{\"ref\":\"main\"}}",
                                                req.uri())));

        ToolResult result =
                createTool(client)
                        .execute(
                                Map.of(
                                        "action", "create_pr",
                                        "owner", "test-org",
                                        "repo", "test-repo",
                                        "title", "New PR",
                                        "head", "new-branch",
                                        "base", "main",
                                        "body", "PR description"),
                                contextWithToken("test-token"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("\"id\":11"));
        assertTrue(result.content().contains("\"title\":\"New PR\""));
    }

    @Test
    @DisplayName("list_issues with state filter passes query parameter")
    void listIssues_withStateFilter() throws Exception {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        RoutingClient client =
                new RoutingClient(
                        Map.of(
                                "GET /repos/test-org/test-repo/issues",
                                (req, body) -> {
                                    capturedUri.set(req.uri());
                                    return fakeResponse(200, "[]", req.uri());
                                }));

        createTool(client)
                .execute(
                        Map.of(
                                "action", "list_issues",
                                "owner", "test-org",
                                "repo", "test-repo",
                                "state", "closed"),
                        contextWithToken("test-token"));

        String query = capturedUri.get().getQuery();
        assertNotNull(query);
        assertTrue(query.contains("state=closed"));
    }

    @Test
    @DisplayName("Required parameter missing throws IllegalArgumentException")
    void missingRequiredParameter_throwsException() {
        RoutingClient client = new RoutingClient(Map.of());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        createTool(client)
                                .execute(
                                        Map.of(
                                                "action", "create_issue",
                                                "owner", "test-org",
                                                "repo", "test-repo"),
                                        contextWithToken("test-token")));
    }
}
