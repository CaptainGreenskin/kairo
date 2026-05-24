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
package io.kairo.channel.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.Experimental;
import io.kairo.api.gateway.SendResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Thin JDK-HTTP client that POSTs a DingTalk text payload to the custom-bot webhook URL and maps
 * the response into a {@link SendResult}.
 *
 * <p>Failure classification rules (aligned with {@link SendResult.FailureMode}):
 *
 * <ul>
 *   <li>HTTP 2xx with {@code errcode == 0} → {@link SendResult#ok(String)} carrying no remote id
 *       (DingTalk does not return one for bot messages).
 *   <li>HTTP 2xx with DingTalk errcode 130101 / 130102 / 130103 (rate-limit family) → {@link
 *       SendResult.FailureMode#RATE_LIMITED}.
 *   <li>Any other HTTP 2xx with {@code errcode != 0} → {@link SendResult.FailureMode#PERMANENT}.
 *   <li>HTTP 429 → {@link SendResult.FailureMode#RATE_LIMITED}.
 *   <li>HTTP 4xx / 5xx → {@link SendResult.FailureMode#TRANSIENT}.
 *   <li>Transport/IO failure → {@link SendResult.FailureMode#TRANSIENT}.
 * </ul>
 *
 * @since v0.9.1 (Experimental)
 */
@Experimental("DingTalk outbound client — contract may change in v0.10")
public final class DingTalkOutboundClient {

    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI webhookUri;
    private final DingTalkSignatureVerifier signer;
    private final Duration timeout;

    public DingTalkOutboundClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String webhookUrl,
            DingTalkSignatureVerifier signer,
            Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.webhookUri = URI.create(Objects.requireNonNull(webhookUrl, "webhookUrl"));
        this.signer = signer;
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /** Send {@code payload} as a DingTalk bot message; never throws across the {@link Mono}. */
    public Mono<SendResult> send(Map<String, Object> payload) {
        return Mono.fromCallable(() -> doSend(payload))
                .onErrorResume(
                        e ->
                                Mono.just(
                                        SendResult.fail(
                                                SendResult.FailureMode.TRANSIENT,
                                                "DingTalk send failed: " + e.getMessage())));
    }

    private SendResult doSend(Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        URI target = signer != null ? signedUri(webhookUri, signer) : webhookUri;
        HttpRequest request =
                HttpRequest.newBuilder(target)
                        .timeout(timeout)
                        .header("Content-Type", CONTENT_TYPE_JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status == 429) {
            return SendResult.fail(SendResult.FailureMode.RATE_LIMITED, "HTTP 429 from DingTalk");
        }
        if (status < 200 || status >= 300) {
            return SendResult.fail(SendResult.FailureMode.TRANSIENT, "DingTalk HTTP " + status);
        }
        return classifyJsonBody(response.body());
    }

    private SendResult classifyJsonBody(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            int errcode = node.path("errcode").asInt(-1);
            String errmsg = node.path("errmsg").asText("");
            if (errcode == 0) {
                return SendResult.ok(null);
            }
            if (isRateLimitErrCode(errcode)) {
                return SendResult.fail(
                        SendResult.FailureMode.RATE_LIMITED, "errcode=" + errcode + " " + errmsg);
            }
            return SendResult.fail(
                    SendResult.FailureMode.PERMANENT, "errcode=" + errcode + " " + errmsg);
        } catch (Exception e) {
            return SendResult.fail(
                    SendResult.FailureMode.TRANSIENT,
                    "unparseable response body: " + e.getMessage());
        }
    }

    private static boolean isRateLimitErrCode(int errcode) {
        // DingTalk bot API rate-limit family: 130101 (20/min), 130102 (20/min burst),
        // 130103 (global throttle). Kept as a small closed set so dashboards stay consistent.
        return errcode == 130101 || errcode == 130102 || errcode == 130103;
    }

    private static URI signedUri(URI base, DingTalkSignatureVerifier signer) {
        long ts = System.currentTimeMillis();
        String sig = signer.sign(ts);
        String sep = base.getQuery() == null ? "?" : "&";
        String extra =
                sep
                        + "timestamp="
                        + ts
                        + "&sign="
                        + java.net.URLEncoder.encode(sig, StandardCharsets.UTF_8);
        return URI.create(base.toString() + extra);
    }
}
