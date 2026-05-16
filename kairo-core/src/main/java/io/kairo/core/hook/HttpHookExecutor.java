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
package io.kairo.core.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.hook.ExternalHookConfig;
import io.kairo.api.hook.ExternalHookExecutor;
import io.kairo.api.hook.HookEvent;
import io.kairo.api.hook.HookResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Executes external hooks via HTTP POST, following a JSON request/response protocol.
 *
 * <p>Protocol:
 *
 * <ul>
 *   <li>POST the serialized {@link HookEvent} as JSON body to the configured URL
 *   <li>Headers support {@code $ENV_VAR} interpolation (only for vars listed in {@code
 *       allowedEnvVars})
 *   <li>Response body is parsed as JSON with optional {@code decision}, {@code reason}, {@code
 *       modifiedInput} fields
 *   <li>HTTP 2xx = success, parse response body
 *   <li>HTTP 4xx/5xx = CONTINUE with warning logged
 * </ul>
 */
public class HttpHookExecutor implements ExternalHookExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpHookExecutor.class);

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpHookExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient =
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public HttpHookExecutor(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String type() {
        return "http";
    }

    @Override
    public <T extends HookEvent> Mono<HookResult<T>> execute(T event, ExternalHookConfig config) {
        if (config.url() == null || config.url().isBlank()) {
            return Mono.just(HookResult.proceed(event));
        }

        return Mono.fromCallable(() -> executeBlocking(event, config))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(
                        e -> {
                            log.warn("HTTP hook failed [{}]: {}", config.url(), e.getMessage());
                            return Mono.just(HookResult.proceed(event));
                        });
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> HookResult<T> executeBlocking(T event, ExternalHookConfig config)
            throws Exception {
        byte[] body = objectMapper.writeValueAsBytes(event);

        HttpRequest.Builder requestBuilder =
                HttpRequest.newBuilder()
                        .uri(URI.create(config.url()))
                        .timeout(config.timeout())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        for (Map.Entry<String, String> entry : config.headers().entrySet()) {
            String value = interpolateEnvVars(entry.getValue(), config);
            requestBuilder.header(entry.getKey(), value);
        }

        HttpResponse<String> response =
                httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            log.warn(
                    "HTTP hook returned status {} [{}]: {}", status, config.url(), response.body());
            return HookResult.proceed(event);
        }

        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            return HookResult.proceed(event);
        }

        return parseResponse(event, responseBody, config.url());
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> HookResult<T> parseResponse(T event, String body, String url) {
        try {
            JsonNode root = objectMapper.readTree(body);

            String decisionStr = root.has("decision") ? root.get("decision").asText() : "CONTINUE";
            HookResult.Decision decision;
            try {
                decision = HookResult.Decision.valueOf(decisionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown decision '{}' from HTTP hook [{}]", decisionStr, url);
                decision = HookResult.Decision.CONTINUE;
            }

            String reason = root.has("reason") ? root.get("reason").asText() : null;

            Map<String, Object> modifiedInput = null;
            if (root.has("modifiedInput")) {
                modifiedInput = objectMapper.convertValue(root.get("modifiedInput"), Map.class);
            }

            String injectedContext =
                    root.has("injectedContext") ? root.get("injectedContext").asText() : null;

            return new HookResult<>(
                    event, decision, injectedContext, modifiedInput, reason, null, url);
        } catch (Exception e) {
            log.warn("Failed to parse HTTP hook response [{}]: {}", url, e.getMessage());
            return HookResult.proceed(event);
        }
    }

    static String interpolateEnvVars(String value, ExternalHookConfig config) {
        if (value == null || !value.contains("$")) return value;
        Matcher m = ENV_VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String varName = m.group(1);
            if (config.allowedEnvVars().contains(varName)) {
                String envVal = System.getenv(varName);
                m.appendReplacement(sb, envVal != null ? Matcher.quoteReplacement(envVal) : "");
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
