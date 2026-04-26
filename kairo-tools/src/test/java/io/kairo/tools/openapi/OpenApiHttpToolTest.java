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
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;

class OpenApiHttpToolTest {

    private static class FakeHttpClient extends HttpClient {

        private String responseBody = "";
        private IOException throwOnSend = null;
        private HttpRequest lastRequest;

        void setResponseBody(String body) {
            this.responseBody = body;
        }

        void throwOnSend(IOException ex) {
            this.throwOnSend = ex;
        }

        HttpRequest lastRequest() {
            return lastRequest;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler)
                throws IOException {
            lastRequest = req;
            if (throwOnSend != null) throw throwOnSend;
            String body = responseBody;
            return (HttpResponse<T>)
                    new HttpResponse<String>() {
                        public int statusCode() {
                            return 200;
                        }

                        public String body() {
                            return body;
                        }

                        public HttpRequest request() {
                            return req;
                        }

                        public Optional<HttpResponse<String>> previousResponse() {
                            return Optional.empty();
                        }

                        public HttpHeaders headers() {
                            return HttpHeaders.of(Map.of(), (a, b) -> true);
                        }

                        public Optional<javax.net.ssl.SSLSession> sslSession() {
                            return Optional.empty();
                        }

                        public java.net.URI uri() {
                            return req.uri();
                        }

                        public HttpClient.Version version() {
                            return HttpClient.Version.HTTP_1_1;
                        }
                    };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest req, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest req,
                HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> push) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Optional<Executor> executor() {
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
        public Version version() {
            return Version.HTTP_1_1;
        }
    }

    @Test
    void getRequestWithNoParams() {
        FakeHttpClient client = new FakeHttpClient();
        client.setResponseBody("{\"ok\":true}");

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com", client);
        String result = tool.execute("GET", "/health", Map.of(), Set.of(), Set.of());

        assertEquals("{\"ok\":true}", result);
    }

    @Test
    void pathParamSubstitution() {
        FakeHttpClient client = new FakeHttpClient();
        client.setResponseBody("{}");

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com", client);
        tool.execute("GET", "/users/{id}", Map.of("id", "42"), Set.of("id"), Set.of());

        assertTrue(client.lastRequest().uri().toString().contains("/users/42"));
    }

    @Test
    void queryParamAppended() {
        FakeHttpClient client = new FakeHttpClient();
        client.setResponseBody("[]");

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com", client);
        tool.execute("GET", "/search", Map.of("q", "hello"), Set.of(), Set.of("q"));

        assertTrue(client.lastRequest().uri().toString().contains("q=hello"));
    }

    @Test
    void trailingSlashInBaseUrlRemoved() {
        FakeHttpClient client = new FakeHttpClient();
        client.setResponseBody("ok");

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com/", client);
        tool.execute("GET", "/ping", Map.of(), Set.of(), Set.of());

        assertFalse(client.lastRequest().uri().toString().contains("example.com//"));
    }

    @Test
    void postRequestSendsBody() {
        FakeHttpClient client = new FakeHttpClient();
        client.setResponseBody("{\"id\":1}");

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com", client);
        String result =
                tool.execute("POST", "/items", Map.of("name", "widget"), Set.of(), Set.of());

        assertEquals("{\"id\":1}", result);
        assertEquals("POST", client.lastRequest().method());
    }

    @Test
    void ioExceptionReturnsErrorJson() {
        FakeHttpClient client = new FakeHttpClient();
        client.throwOnSend(new IOException("timeout"));

        OpenApiHttpTool tool = new OpenApiHttpTool("https://api.example.com", client);
        String result = tool.execute("GET", "/fail", Map.of(), Set.of(), Set.of());

        assertTrue(result.contains("error"));
        assertTrue(result.contains("timeout"));
    }
}
