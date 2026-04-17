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
package io.kairo.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared utility methods for model providers. Stateless — all methods are static. Providers call
 * these helpers without inheritance, preserving full independence of their API-specific logic.
 */
public final class ModelProviderUtils {

    private static final Logger log = LoggerFactory.getLogger(ModelProviderUtils.class);

    private ModelProviderUtils() {} // prevent instantiation

    /**
     * Create a configured {@link HttpClient} with the specified connect timeout.
     *
     * @param connectTimeout the connection timeout
     * @return a new HttpClient instance
     */
    public static HttpClient createHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    /**
     * Create a configured {@link ObjectMapper} for JSON serialization.
     *
     * @return a new ObjectMapper instance
     */
    public static ObjectMapper createObjectMapper() {
        return new ObjectMapper();
    }

    /**
     * Validate that an API key is present and non-blank.
     *
     * @param apiKey the API key to validate
     * @param providerName the provider name for the error message
     * @throws IllegalArgumentException if the key is null or blank
     */
    public static void validateApiKey(String apiKey, String providerName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(providerName + " apiKey cannot be null or blank");
        }
    }

    /**
     * Validate that a base URL uses HTTPS to prevent API key leakage over plaintext HTTP.
     *
     * <p>Logs a warning for non-HTTPS URLs but does not throw, to allow localhost development.
     * Throws for null or blank URLs.
     *
     * @param baseUrl the base URL to validate
     * @param providerName the provider name for the log message
     * @throws IllegalArgumentException if the URL is null or blank
     */
    public static void validateBaseUrl(String baseUrl, String providerName) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException(providerName + " baseUrl cannot be null or blank");
        }
        if (!baseUrl.startsWith("https://")) {
            if (baseUrl.startsWith("http://localhost") || baseUrl.startsWith("http://127.0.0.1")) {
                log.debug("{} using localhost HTTP endpoint: {}", providerName, baseUrl);
            } else {
                log.warn(
                        "SECURITY WARNING: {} baseUrl '{}' does not use HTTPS. "
                                + "API keys will be transmitted in plaintext. "
                                + "Use https:// in production.",
                        providerName,
                        baseUrl);
            }
        }
    }

    /**
     * Sanitize a response body for safe logging by masking sensitive tokens such as bearer tokens
     * and API keys.
     *
     * @param body the response body to sanitize
     * @return the sanitized string
     */
    public static String sanitizeForLogging(String body) {
        if (body == null) return "(empty)";
        return body.replaceAll(
                "(?i)(bearer|api[_-]?key|authorization)[\"':\\s]*[\"']?[\\w\\-\\.]+", "$1: ***");
    }

    /**
     * Parse the Retry-After header value to seconds.
     *
     * @param retryAfter the raw Retry-After header value
     * @return the parsed seconds, or null if the value is absent or unparseable
     */
    public static Long parseRetryAfter(String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) return null;
        try {
            return Long.parseLong(retryAfter.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
