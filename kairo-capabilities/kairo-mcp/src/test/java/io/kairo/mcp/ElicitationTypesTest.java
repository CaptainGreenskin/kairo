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
package io.kairo.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ElicitationTypesTest {

    // --- ElicitationAction ---

    @Test
    void elicitationAction_threeConstants() {
        assertEquals(3, ElicitationAction.values().length);
    }

    @Test
    void elicitationAction_valueOfRoundtrip() {
        assertEquals(ElicitationAction.ACCEPT, ElicitationAction.valueOf("ACCEPT"));
        assertEquals(ElicitationAction.DECLINE, ElicitationAction.valueOf("DECLINE"));
        assertEquals(ElicitationAction.CANCEL, ElicitationAction.valueOf("CANCEL"));
    }

    // --- ElicitationRequest ---

    @Test
    void request_messageAccessor() {
        ElicitationRequest req = new ElicitationRequest("What is your name?", Map.of());
        assertEquals("What is your name?", req.message());
    }

    @Test
    void request_schemaAccessor() {
        ElicitationRequest req = new ElicitationRequest("msg", Map.of("type", "string"));
        assertEquals("string", req.requestedSchema().get("type"));
    }

    @Test
    void request_nullSchemaDefaultsToEmpty() {
        ElicitationRequest req = new ElicitationRequest("msg", null);
        assertTrue(req.requestedSchema().isEmpty());
    }

    @Test
    void request_nullMessageThrows() {
        assertThrows(NullPointerException.class, () -> new ElicitationRequest(null, Map.of()));
    }

    @Test
    void request_schemaIsUnmodifiable() {
        ElicitationRequest req = new ElicitationRequest("msg", Map.of("k", "v"));
        assertThrows(
                UnsupportedOperationException.class, () -> req.requestedSchema().put("x", "y"));
    }

    @Test
    void request_equalsAndHashCode() {
        ElicitationRequest a = new ElicitationRequest("msg", Map.of());
        ElicitationRequest b = new ElicitationRequest("msg", Map.of());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void request_toStringContainsMessage() {
        assertTrue(new ElicitationRequest("hello", Map.of()).toString().contains("hello"));
    }

    // --- ElicitationResponse ---

    @Test
    void response_acceptFactory() {
        ElicitationResponse r = ElicitationResponse.accept(Map.of("name", "Alice"));
        assertEquals(ElicitationAction.ACCEPT, r.action());
        assertEquals("Alice", r.data().get("name"));
    }

    @Test
    void response_declineFactory() {
        ElicitationResponse r = ElicitationResponse.decline();
        assertEquals(ElicitationAction.DECLINE, r.action());
        assertTrue(r.data().isEmpty());
    }

    @Test
    void response_cancelFactory() {
        ElicitationResponse r = ElicitationResponse.cancel();
        assertEquals(ElicitationAction.CANCEL, r.action());
        assertTrue(r.data().isEmpty());
    }

    @Test
    void response_nullDataDefaultsToEmpty() {
        ElicitationResponse r = new ElicitationResponse(ElicitationAction.ACCEPT, null);
        assertTrue(r.data().isEmpty());
    }

    @Test
    void response_equalsAndHashCode() {
        ElicitationResponse a = ElicitationResponse.decline();
        ElicitationResponse b = ElicitationResponse.decline();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void response_toStringContainsAction() {
        assertTrue(ElicitationResponse.cancel().toString().contains("CANCEL"));
    }
}
