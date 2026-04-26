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

import static org.assertj.core.api.Assertions.assertThat;

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
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class OpenApiHttpToolTest {

    private static class FakeHttpClient extends HttpClient {

        private final String responseBody;
        private final boolean throwIo;

        FakeHttpClient(String responseBody) {
            this.responseBody = responseBody;
            this.throwIo = false;
        }

        FakeHttpClient() {
            this.responseBody = null;
            this.throwIo = true;
        }

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
            try {
                return SSLContext.getDefault();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
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
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException {
            if (throwIo) {
                throw new IOException("connection refused");
            }
            String body = responseBody;
            return (HttpResponse<T>)
                    new HttpResponse<String>() {
                        @Override
                        public int statusCode() {
                            return 200;
                        }

                        @Override
                        public HttpRequest request() {
                            return request;
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
                        public java.net.URI uri() {
                            return request.uri();
                        }

                        @Override
                        public Version version() {
                            return Version.HTTP_1_1;
                        }
                    };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void getRequestReturnsResponseBody() {
        OpenApiHttpTool tool =
                new OpenApiHttpTool("https://example.com", new FakeHttpClient("{\"ok\":true}"));
        String result = tool.execute("GET", "/users", Map.of(), Set.of(), Set.of());
        assertThat(result).isEqualTo("{\"ok\":true}");
    }

    @Test
    void pathParamIsSubstituted() {
        OpenApiHttpTool tool =
                new OpenApiHttpTool("https://example.com", new FakeHttpClient("found"));
        String result =
                tool.execute("GET", "/users/{id}", Map.of("id", "42"), Set.of("id"), Set.of());
        assertThat(result).isEqualTo("found");
    }

    @Test
    void postRequestReturnsResponseBody() {
        OpenApiHttpTool tool =
                new OpenApiHttpTool(
                        "https://example.com", new FakeHttpClient("{\"created\":true}"));
        String result = tool.execute("POST", "/users", Map.of("name", "Alice"), Set.of(), Set.of());
        assertThat(result).isEqualTo("{\"created\":true}");
    }

    @Test
    void ioExceptionReturnsErrorJson() {
        OpenApiHttpTool tool = new OpenApiHttpTool("https://example.com", new FakeHttpClient());
        String result = tool.execute("GET", "/fail", Map.of(), Set.of(), Set.of());
        assertThat(result).contains("error");
    }
}
