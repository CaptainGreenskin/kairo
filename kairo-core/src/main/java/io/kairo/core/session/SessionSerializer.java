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
package io.kairo.core.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes and deserializes {@link SessionSnapshot} instances to/from versioned JSON.
 *
 * <p>The JSON envelope includes a {@code schemaVersion} field for forward compatibility. Unknown
 * fields are silently ignored during deserialization.
 */
public class SessionSerializer {

    private static final int SCHEMA_VERSION = 1;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    /** Create a new SessionSerializer with default Jackson configuration. */
    public SessionSerializer() {
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Returns the pre-configured {@link ObjectMapper} used by this serializer.
     *
     * <p>The mapper is configured with {@link JavaTimeModule}, ISO-8601 date formatting, and
     * lenient unknown-property handling. Other components that need consistent JSON serialization
     * (e.g. {@code JsonFileSnapshotStore}) should reuse this instance rather than creating a new
     * one.
     *
     * @return the shared ObjectMapper instance
     */
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * Serialize a session snapshot to versioned JSON.
     *
     * @param snapshot the snapshot to serialize
     * @return pretty-printed JSON string
     * @throws RuntimeException if serialization fails
     */
    public String serialize(SessionSnapshot snapshot) {
        try {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("schemaVersion", SCHEMA_VERSION);
            wrapper.put("sessionId", snapshot.sessionId());
            wrapper.put("createdAt", snapshot.createdAt().toString());
            wrapper.put("updatedAt", Instant.now().toString());
            wrapper.put("turnCount", snapshot.turnCount());
            wrapper.put("messages", snapshot.messages());
            wrapper.put("agentState", snapshot.agentState());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to serialize session snapshot: " + snapshot.sessionId(), e);
        }
    }

    /**
     * Deserialize a JSON string into a {@link SessionSnapshot}.
     *
     * <p>Checks {@code schemaVersion} and handles forward compatibility by ignoring unknown fields.
     *
     * @param json the JSON string
     * @return the deserialized snapshot
     * @throws RuntimeException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public SessionSnapshot deserialize(String json) {
        try {
            Map<String, Object> wrapper = objectMapper.readValue(json, MAP_TYPE);

            // Check schema version for forward compat
            int version =
                    wrapper.containsKey("schemaVersion")
                            ? ((Number) wrapper.get("schemaVersion")).intValue()
                            : 1;

            if (version > SCHEMA_VERSION) {
                // Future version — best-effort parse, ignore unknown fields
            }

            String sessionId = (String) wrapper.get("sessionId");
            Instant createdAt = Instant.parse((String) wrapper.get("createdAt"));
            int turnCount =
                    wrapper.containsKey("turnCount")
                            ? ((Number) wrapper.get("turnCount")).intValue()
                            : 0;

            List<Map<String, Object>> messages =
                    wrapper.containsKey("messages")
                            ? (List<Map<String, Object>>) wrapper.get("messages")
                            : List.of();

            Map<String, Object> agentState =
                    wrapper.containsKey("agentState")
                            ? (Map<String, Object>) wrapper.get("agentState")
                            : Map.of();

            return new SessionSnapshot(sessionId, createdAt, turnCount, messages, agentState);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize session snapshot", e);
        }
    }

    /**
     * Extract lightweight metadata from a JSON string without parsing the full message payload.
     *
     * @param json the JSON string
     * @return session metadata
     * @throws RuntimeException if parsing fails
     */
    public SessionMetadata extractMetadata(String json) {
        try {
            Map<String, Object> wrapper = objectMapper.readValue(json, MAP_TYPE);
            String sessionId = (String) wrapper.get("sessionId");
            Instant createdAt = Instant.parse((String) wrapper.get("createdAt"));
            Instant updatedAt =
                    wrapper.containsKey("updatedAt")
                            ? Instant.parse((String) wrapper.get("updatedAt"))
                            : createdAt;
            int turnCount =
                    wrapper.containsKey("turnCount")
                            ? ((Number) wrapper.get("turnCount")).intValue()
                            : 0;
            return new SessionMetadata(sessionId, createdAt, updatedAt, turnCount);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract session metadata", e);
        }
    }
}
