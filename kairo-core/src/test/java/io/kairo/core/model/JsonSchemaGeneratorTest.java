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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSchemaGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void stringType() {
        ObjectNode schema = JsonSchemaGenerator.generateSchema(String.class, mapper);
        assertEquals("string", schema.path("type").asText());
    }

    @Test
    void integerTypes() {
        assertEquals("integer", JsonSchemaGenerator.generateSchema(int.class, mapper).path("type").asText());
        assertEquals("integer", JsonSchemaGenerator.generateSchema(Integer.class, mapper).path("type").asText());
        assertEquals("integer", JsonSchemaGenerator.generateSchema(long.class, mapper).path("type").asText());
        assertEquals("integer", JsonSchemaGenerator.generateSchema(Long.class, mapper).path("type").asText());
    }

    @Test
    void numberTypes() {
        assertEquals("number", JsonSchemaGenerator.generateSchema(double.class, mapper).path("type").asText());
        assertEquals("number", JsonSchemaGenerator.generateSchema(Float.class, mapper).path("type").asText());
    }

    @Test
    void booleanType() {
        assertEquals("boolean", JsonSchemaGenerator.generateSchema(boolean.class, mapper).path("type").asText());
        assertEquals("boolean", JsonSchemaGenerator.generateSchema(Boolean.class, mapper).path("type").asText());
    }

    enum Color { RED, GREEN, BLUE }

    @Test
    void enumType() {
        ObjectNode schema = JsonSchemaGenerator.generateSchema(Color.class, mapper);
        assertEquals("string", schema.path("type").asText());
        JsonNode enumValues = schema.path("enum");
        assertTrue(enumValues.isArray());
        assertEquals(3, enumValues.size());
    }

    static class SimplePojo {
        public String name;
        public int age;
    }

    @Test
    void pojoType() {
        ObjectNode schema = JsonSchemaGenerator.generateSchema(SimplePojo.class, mapper);
        assertEquals("object", schema.path("type").asText());
        JsonNode props = schema.path("properties");
        assertTrue(props.has("name"));
        assertTrue(props.has("age"));
        assertEquals("string", props.path("name").path("type").asText());
        assertEquals("integer", props.path("age").path("type").asText());
        assertFalse(schema.path("additionalProperties").asBoolean(true));
    }

    record SimpleRecord(String title, double score) {}

    @Test
    void recordType() {
        ObjectNode schema = JsonSchemaGenerator.generateSchema(SimpleRecord.class, mapper);
        assertEquals("object", schema.path("type").asText());
        JsonNode props = schema.path("properties");
        assertTrue(props.has("title"));
        assertTrue(props.has("score"));
        assertEquals("string", props.path("title").path("type").asText());
        assertEquals("number", props.path("score").path("type").asText());
        JsonNode required = schema.path("required");
        assertTrue(required.isArray());
        assertEquals(2, required.size());
    }

    static class AnnotatedPojo {
        @JsonProperty("full_name")
        public String name;
        public int count;
    }

    @Test
    void jsonPropertyAnnotation() {
        ObjectNode schema = JsonSchemaGenerator.generateSchema(AnnotatedPojo.class, mapper);
        JsonNode props = schema.path("properties");
        assertTrue(props.has("full_name"));
        assertFalse(props.has("name"));
    }

    @Test
    void mapType() {
        ObjectNode schema = JsonSchemaGenerator.generateSchema(Map.class, mapper);
        assertEquals("object", schema.path("type").asText());
    }

    static class NestedPojo {
        public String label;
        public SimplePojo nested;
    }

    @Test
    void nestedPojoType() {
        ObjectNode schema = JsonSchemaGenerator.generateSchema(NestedPojo.class, mapper);
        JsonNode nested = schema.path("properties").path("nested");
        assertEquals("object", nested.path("type").asText());
        assertTrue(nested.path("properties").has("name"));
    }
}
