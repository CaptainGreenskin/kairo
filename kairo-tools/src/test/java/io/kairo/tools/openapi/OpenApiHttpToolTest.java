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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiHttpToolTest {

    /** Minimal abstract base — overrides all abstract HttpClient methods with stubs. */
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

    /** Build a fake HttpResponse returning the given body. */
    private static HttpResponse<String> fakeResponse(String body, URI uri) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return 200;
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

    /** Simple client that always returns {@code responseBody}. */
    private static StubHttpClient fixedResponseClient(String responseBody) {
        return new StubHttpClient() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) {
                return (HttpResponse<T>) fakeResponse(responseBody, req.uri());
            }
        };
    }

    @Test
    @DisplayName("GET request with no params returns response body")
    void get_noParams_returnsBody() {
        OpenApiHttpTool tool =
                new OpenApiHttpTool(
                        "https://api.example.com", fixedResponseClient("{\"ok\":true}"));
        assertEquals("{\"ok\":true}", tool.execute("GET", "/users", Map.of(), Set.of(), Set.of()));
    }

    @Test
    @DisplayName("Path parameters are substituted into URL template")
    void pathParams_substitutedIntoUrl() {
        List<URI> captured = new ArrayList<>();
        StubHttpClient client =
                new StubHttpClient() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> HttpResponse<T> send(
                            HttpRequest req, HttpResponse.BodyHandler<T> handler) {
                        captured.add(req.uri());
                        return (HttpResponse<T>) fakeResponse("{}", req.uri());
                    }
                };

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com", client);
        tool.execute("GET", "/users/{userId}", Map.of("userId", "42"), Set.of("userId"), Set.of());

        assertEquals(1, captured.size());
        assertTrue(captured.get(0).toString().contains("/users/42"));
        assertFalse(captured.get(0).toString().contains("{userId}"));
    }

    @Test
    @DisplayName("Query parameters are appended to URL")
    void queryParams_appendedToUrl() {
        List<URI> captured = new ArrayList<>();
        StubHttpClient client =
                new StubHttpClient() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> HttpResponse<T> send(
                            HttpRequest req, HttpResponse.BodyHandler<T> handler) {
                        captured.add(req.uri());
                        return (HttpResponse<T>) fakeResponse("[]", req.uri());
                    }
                };

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com", client);
        tool.execute("GET", "/items", Map.of("page", 2), Set.of(), Set.of("page"));

        String url = captured.get(0).toString();
        assertTrue(url.contains("page=2"), "URL should contain query param: " + url);
    }

    @Test
    @DisplayName("POST request returns response body")
    void post_returnsBody() {
        OpenApiHttpTool tool =
                new OpenApiHttpTool("https://api.example.com", fixedResponseClient("{\"id\":1}"));
        String result = tool.execute("POST", "/users", Map.of("name", "Alice"), Set.of(), Set.of());
        assertEquals("{\"id\":1}", result);
    }

    @Test
    @DisplayName("Trailing slash on baseUrl is stripped — no double slash in URL")
    void baseUrl_trailingSlashStripped() {
        List<URI> captured = new ArrayList<>();
        StubHttpClient client =
                new StubHttpClient() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> HttpResponse<T> send(
                            HttpRequest req, HttpResponse.BodyHandler<T> handler) {
                        captured.add(req.uri());
                        return (HttpResponse<T>) fakeResponse("ok", req.uri());
                    }
                };

        new OpenApiHttpTool("https://api.example.com/", client)
                .execute("GET", "/ping", Map.of(), Set.of(), Set.of());

        assertFalse(captured.get(0).toString().contains("//ping"), "Should not have double slash");
    }

    @Test
    @DisplayName("IOException returns JSON error string")
    void ioException_returnsErrorJson() {
        StubHttpClient failingClient =
                new StubHttpClient() {
                    @Override
                    public <T> HttpResponse<T> send(
                            HttpRequest req, HttpResponse.BodyHandler<T> handler)
                            throws IOException {
                        throw new IOException("connection refused");
                    }
                };

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com", failingClient);
        String result = tool.execute("GET", "/ping", Map.of(), Set.of(), Set.of());

        assertTrue(result.startsWith("{\"error\":"), "Should return error JSON: " + result);
        assertTrue(result.contains("connection refused"));
    }

    @Test
    @DisplayName("DELETE method is dispatched without body")
    void delete_dispatched() {
        OpenApiHttpTool tool =
                new OpenApiHttpTool("https://api.example.com", fixedResponseClient("{}"));
        assertEquals("{}", tool.execute("DELETE", "/users/1", Map.of(), Set.of(), Set.of()));
    }
}
