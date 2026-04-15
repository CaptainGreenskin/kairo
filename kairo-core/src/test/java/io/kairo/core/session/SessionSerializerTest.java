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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionSerializerTest {

    private SessionSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new SessionSerializer();
    }

    @Test
    @DisplayName("Serialize and deserialize round trip preserves data")
    void serializeAndDeserializeRoundTrip() {
        Instant created = Instant.parse("2025-06-01T10:00:00Z");
        SessionSnapshot snapshot =
                new SessionSnapshot(
                        "sess-1",
                        created,
                        5,
                        List.of(Map.of("role", "user", "text", "hello")),
                        Map.of("planMode", true));

        String json = serializer.serialize(snapshot);
        SessionSnapshot restored = serializer.deserialize(json);

        assertEquals("sess-1", restored.sessionId());
        assertEquals(created, restored.createdAt());
        assertEquals(5, restored.turnCount());
        assertEquals(1, restored.messages().size());
        assertEquals("hello", restored.messages().get(0).get("text"));
        assertEquals(true, restored.agentState().get("planMode"));
    }

    @Test
    @DisplayName("Serialization includes schemaVersion field")
    void serializationIncludesSchemaVersion() {
        SessionSnapshot snapshot =
                new SessionSnapshot("sess-1", Instant.now(), 0, List.of(), Map.of());
        String json = serializer.serialize(snapshot);
        assertTrue(json.contains("\"schemaVersion\""), "JSON must contain schemaVersion");
        assertTrue(json.contains("\"schemaVersion\" : 1"), "Schema version must be 1");
    }

    @Test
    @DisplayName("Deserialization ignores unknown fields (forward compat)")
    void deserializationIgnoresUnknownFields() {
        String json =
                """
                {
                    "schemaVersion": 2,
                    "sessionId": "sess-future",
                    "createdAt": "2025-06-01T10:00:00Z",
                    "updatedAt": "2025-06-01T11:00:00Z",
                    "turnCount": 3,
                    "messages": [],
                    "agentState": {},
                    "unknownField": "should be ignored",
                    "anotherNewField": 42
                }
                """;
        SessionSnapshot snapshot = serializer.deserialize(json);
        assertEquals("sess-future", snapshot.sessionId());
        assertEquals(3, snapshot.turnCount());
    }

    @Test
    @DisplayName("Deserialization handles null/missing messages gracefully")
    void deserializationHandlesNullMessages() {
        String json =
                """
                {
                    "schemaVersion": 1,
                    "sessionId": "sess-no-msgs",
                    "createdAt": "2025-06-01T10:00:00Z",
                    "turnCount": 0,
                    "agentState": {}
                }
                """;
        SessionSnapshot snapshot = serializer.deserialize(json);
        assertNotNull(snapshot.messages());
        assertTrue(snapshot.messages().isEmpty());
    }

    @Test
    @DisplayName("Serialization formats as pretty-printed JSON")
    void serializationFormatsAsPrettyJson() {
        SessionSnapshot snapshot =
                new SessionSnapshot("sess-1", Instant.now(), 0, List.of(), Map.of());
        String json = serializer.serialize(snapshot);
        assertTrue(json.contains("\n"), "Pretty printed JSON should contain newlines");
        assertTrue(json.contains("  "), "Pretty printed JSON should contain indentation");
    }

    @Test
    @DisplayName("Deserialization preserves timestamps")
    void deserializationPreservesTimestamps() {
        Instant created = Instant.parse("2025-03-15T08:30:00Z");
        SessionSnapshot original = new SessionSnapshot("sess-ts", created, 2, List.of(), Map.of());

        String json = serializer.serialize(original);
        SessionSnapshot restored = serializer.deserialize(json);

        assertEquals(created, restored.createdAt());
    }
}
