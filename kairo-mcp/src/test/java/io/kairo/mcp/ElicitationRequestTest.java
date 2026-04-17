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

class ElicitationRequestTest {

    @Test
    void constructorSetsFieldsCorrectly() {
        Map<String, Object> schema = Map.of("type", "object");
        ElicitationRequest request = new ElicitationRequest("Enter data", schema);
        assertEquals("Enter data", request.message());
        assertEquals(schema, request.requestedSchema());
    }

    @Test
    void nullMessageThrowsNullPointerException() {
        assertThrows(
                NullPointerException.class, () -> new ElicitationRequest(null, Map.of()));
    }

    @Test
    void nullSchemaDefaultsToEmptyMap() {
        ElicitationRequest request = new ElicitationRequest("msg", null);
        assertNotNull(request.requestedSchema());
        assertTrue(request.requestedSchema().isEmpty());
    }

    @Test
    void requestedSchemaIsUnmodifiable() {
        ElicitationRequest request =
                new ElicitationRequest("msg", Map.of("key", "value"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.requestedSchema().put("new", "item"));
    }

    @Test
    void equalsAndHashCode() {
        ElicitationRequest a = new ElicitationRequest("msg", Map.of("k", "v"));
        ElicitationRequest b = new ElicitationRequest("msg", Map.of("k", "v"));
        ElicitationRequest c = new ElicitationRequest("other", Map.of("k", "v"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void toStringContainsFields() {
        ElicitationRequest request = new ElicitationRequest("hello", Map.of());
        String str = request.toString();
        assertTrue(str.contains("hello"));
        assertTrue(str.contains("ElicitationRequest"));
    }

    // --- ElicitationResponse tests ---

    @Test
    void acceptFactoryMethodSetsFields() {
        Map<String, Object> data = Map.of("name", "Bob");
        ElicitationResponse response = ElicitationResponse.accept(data);
        assertEquals(ElicitationAction.ACCEPT, response.action());
        assertEquals("Bob", response.data().get("name"));
    }

    @Test
    void declineFactoryMethodSetsFields() {
        ElicitationResponse response = ElicitationResponse.decline();
        assertEquals(ElicitationAction.DECLINE, response.action());
        assertTrue(response.data().isEmpty());
    }

    @Test
    void cancelFactoryMethodSetsFields() {
        ElicitationResponse response = ElicitationResponse.cancel();
        assertEquals(ElicitationAction.CANCEL, response.action());
        assertTrue(response.data().isEmpty());
    }

    @Test
    void responseNullActionThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new ElicitationResponse(null, Map.of()));
    }

    @Test
    void responseNullDataDefaultsToEmptyMap() {
        ElicitationResponse response =
                new ElicitationResponse(ElicitationAction.ACCEPT, null);
        assertNotNull(response.data());
        assertTrue(response.data().isEmpty());
    }

    @Test
    void responseDataIsUnmodifiable() {
        ElicitationResponse response = ElicitationResponse.accept(Map.of("k", "v"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> response.data().put("new", "item"));
    }

    @Test
    void responseEqualsAndHashCode() {
        ElicitationResponse a = ElicitationResponse.accept(Map.of("k", "v"));
        ElicitationResponse b = ElicitationResponse.accept(Map.of("k", "v"));
        ElicitationResponse c = ElicitationResponse.decline();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void responseToStringContainsFields() {
        ElicitationResponse response = ElicitationResponse.accept(Map.of());
        String str = response.toString();
        assertTrue(str.contains("ACCEPT"));
        assertTrue(str.contains("ElicitationResponse"));
    }

    // --- ElicitationAction enum tests ---

    @Test
    void elicitationActionEnumValues() {
        ElicitationAction[] values = ElicitationAction.values();
        assertEquals(3, values.length);
        assertEquals(ElicitationAction.ACCEPT, ElicitationAction.valueOf("ACCEPT"));
        assertEquals(ElicitationAction.DECLINE, ElicitationAction.valueOf("DECLINE"));
        assertEquals(ElicitationAction.CANCEL, ElicitationAction.valueOf("CANCEL"));
    }
}
