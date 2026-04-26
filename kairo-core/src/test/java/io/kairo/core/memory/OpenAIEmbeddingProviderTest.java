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
package io.kairo.core.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class OpenAIEmbeddingProviderTest {

    private MockWebServer server;
    private OpenAIEmbeddingProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        provider = new OpenAIEmbeddingProvider("test-api-key", baseUrl, "text-embedding-3-small");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void embedReturnsSingleVector() {
        String responseJson =
                """
                {"object":"list","data":[{"object":"embedding","embedding":[0.1,0.2,0.3,0.4,0.5],"index":0}],"model":"text-embedding-3-small","usage":{"prompt_tokens":5,"total_tokens":5}}
                """;
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(responseJson));

        StepVerifier.create(provider.embed("hello world"))
                .assertNext(
                        vector -> {
                            assertThat(vector).hasSize(5);
                            assertThat(vector[0])
                                    .isEqualTo(0.1f, org.assertj.core.data.Offset.offset(0.001f));
                            assertThat(vector[4])
                                    .isEqualTo(0.5f, org.assertj.core.data.Offset.offset(0.001f));
                        })
                .verifyComplete();
    }

    @Test
    void embedAllReturnsBatchVectors() {
        String responseJson =
                """
                {"object":"list","data":[{"object":"embedding","embedding":[0.1,0.2,0.3],"index":0},{"object":"embedding","embedding":[0.4,0.5,0.6],"index":1},{"object":"embedding","embedding":[0.7,0.8,0.9],"index":2}],"model":"text-embedding-3-small","usage":{"prompt_tokens":10,"total_tokens":10}}
                """;
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(responseJson));

        List<float[]> results =
                provider.embedAll(List.of("text1", "text2", "text3")).collectList().block();

        assertThat(results).hasSize(3);
        assertThat(results.get(0)[0]).isEqualTo(0.1f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(results.get(1)[0]).isEqualTo(0.4f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(results.get(2)[0]).isEqualTo(0.7f, org.assertj.core.data.Offset.offset(0.001f));
    }

    @Test
    void dimensionsReturns1536() {
        assertThat(provider.dimensions()).isEqualTo(1536);
    }

    @Test
    void httpErrorThrowsException() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(401)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"error\":{\"message\":\"Invalid API key\"}}"));

        StepVerifier.create(provider.embed("test"))
                .expectErrorSatisfies(
                        err -> {
                            assertThat(err).isInstanceOf(RuntimeException.class);
                            assertThat(err.getMessage()).contains("HTTP 401");
                        })
                .verify();
    }

    @Test
    void serverErrorThrowsException() {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(500)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"error\":{\"message\":\"Internal server error\"}}"));

        StepVerifier.create(provider.embed("test"))
                .expectErrorSatisfies(
                        err -> {
                            assertThat(err).isInstanceOf(RuntimeException.class);
                            assertThat(err.getMessage()).contains("HTTP 500");
                        })
                .verify();
    }

    @Test
    void nullApiKeyThrowsNPE() {
        assertThatThrownBy(() -> new OpenAIEmbeddingProvider(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void requestSendsCorrectHeaders() throws Exception {
        String responseJson =
                """
                {"object":"list","data":[{"object":"embedding","embedding":[0.1],"index":0}],"model":"text-embedding-3-small","usage":{"prompt_tokens":1,"total_tokens":1}}
                """;
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(responseJson));

        provider.embed("hello").block();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
        assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");
        assertThat(request.getPath()).isEqualTo("/v1/embeddings");
    }

    @Test
    void requestBodyContainsModelAndInput() throws Exception {
        String responseJson =
                """
                {"object":"list","data":[{"object":"embedding","embedding":[0.1],"index":0}],"model":"text-embedding-3-small","usage":{"prompt_tokens":1,"total_tokens":1}}
                """;
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(responseJson));

        provider.embed("hello").block();

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"model\":\"text-embedding-3-small\"");
        assertThat(body).contains("\"encoding_format\":\"float\"");
        assertThat(body).contains("\"input\":\"hello\"");
    }

    @Test
    void batchRequestUsesArrayInput() throws Exception {
        String responseJson =
                """
                {"object":"list","data":[{"object":"embedding","embedding":[0.1],"index":0},{"object":"embedding","embedding":[0.2],"index":1}],"model":"text-embedding-3-small","usage":{"prompt_tokens":2,"total_tokens":2}}
                """;
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(responseJson));

        provider.embedAll(List.of("hello", "world")).collectList().block();

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"input\":[\"hello\",\"world\"]");
    }

    @Test
    void customBaseUrlIsUsed() throws Exception {
        // The provider was already created with MockWebServer URL, verify it hits the right
        // endpoint
        String responseJson =
                """
                {"object":"list","data":[{"object":"embedding","embedding":[0.1],"index":0}],"model":"text-embedding-3-small","usage":{"prompt_tokens":1,"total_tokens":1}}
                """;
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(responseJson));

        provider.embed("test").block();

        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
