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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelProviderUtilsTest {

    @Test
    void createHttpClientReturnsConfiguredClient() {
        Duration timeout = Duration.ofSeconds(30);
        HttpClient client = ModelProviderUtils.createHttpClient(timeout);

        assertNotNull(client);
        assertTrue(client.connectTimeout().isPresent());
        assertEquals(timeout, client.connectTimeout().get());
    }

    @Test
    void createObjectMapperReturnsConfiguredMapper() throws Exception {
        ObjectMapper mapper = ModelProviderUtils.createObjectMapper();

        assertNotNull(mapper);

        // Verify it can serialize/deserialize basic objects
        Map<String, Object> input = Map.of("key", "value", "num", 42);
        String json = mapper.writeValueAsString(input);
        assertNotNull(json);
        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"value\""));

        // Round-trip
        @SuppressWarnings("unchecked")
        Map<String, Object> deserialized = mapper.readValue(json, Map.class);
        assertEquals("value", deserialized.get("key"));
        assertEquals(42, deserialized.get("num"));
    }

    @Test
    void validateApiKeyAcceptsValidKey() {
        // Should not throw
        assertDoesNotThrow(() -> ModelProviderUtils.validateApiKey("sk-abc123", "TestProvider"));
    }

    @Test
    void validateApiKeyRejectsNull() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ModelProviderUtils.validateApiKey(null, "TestProvider"));
        assertNotNull(ex.getMessage());
    }

    @Test
    void validateApiKeyRejectsBlank() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelProviderUtils.validateApiKey("", "TestProvider"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelProviderUtils.validateApiKey("   ", "TestProvider"));
    }

    @Test
    void validateApiKeyIncludesProviderName() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ModelProviderUtils.validateApiKey(null, "Anthropic"));
        assertTrue(ex.getMessage().contains("Anthropic"), "Exception message should contain the provider name");
    }

    @Test
    void sanitizeForLoggingMasksKey() {
        String body = "{\"Authorization\": \"Bearer sk-longapikey12345\"}";
        String sanitized = ModelProviderUtils.sanitizeForLogging(body);

        assertNotNull(sanitized);
        assertFalse(sanitized.contains("sk-longapikey12345"), "API key should be masked");
        assertTrue(sanitized.contains("***"), "Should contain mask characters");
    }

    @Test
    void sanitizeForLoggingHandlesShortKey() {
        String body = "{\"api_key\": \"sk\"}";
        String sanitized = ModelProviderUtils.sanitizeForLogging(body);

        assertNotNull(sanitized);
        // The regex replaces the key pattern — short key should still be masked
        assertTrue(sanitized.contains("***"), "Short key should be masked");
    }

    @Test
    void sanitizeForLoggingHandlesNull() {
        String result = ModelProviderUtils.sanitizeForLogging(null);

        assertNotNull(result, "Should not return null");
        assertEquals("(empty)", result);
    }
}
