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
package io.kairo.tools.file;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonQueryToolTest {

    private static final ToolContext CTX = new ToolContext("a", "s", Map.of());
    private JsonQueryTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new JsonQueryTool();
    }

    @Test
    void simpleFieldAccess() {
        String json = "{\"name\": \"Alice\", \"age\": 30}";
        ToolResult result = tool.execute(Map.of("json", json, "query", ".name"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("Alice"));
        assertEquals(1, result.metadata().get("resultCount"));
    }

    @Test
    void arrayIndexAccess() {
        String json = "[\"first\", \"second\", \"third\"]";
        ToolResult result = tool.execute(Map.of("json", json, "query", ".[1]"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("second"));
        assertEquals(1, result.metadata().get("resultCount"));
    }

    @Test
    void negativeArrayIndex() {
        String json = "[\"a\", \"b\", \"c\"]";
        ToolResult result = tool.execute(Map.of("json", json, "query", ".[-1]"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("c"));
    }

    @Test
    void nestedFieldAccess() {
        String json = "{\"user\": {\"address\": {\"city\": \"NYC\"}}}";
        ToolResult result =
                tool.execute(Map.of("json", json, "query", ".user.address.city"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("NYC"));
    }

    @Test
    void arrayMappingWithPipe() {
        String json = "[{\"name\": \"Alice\", \"age\": 25}, {\"name\": \"Bob\", \"age\": 30}]";
        ToolResult result = tool.execute(Map.of("json", json, "query", ".[] | .name"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("Alice"));
        assertTrue(result.content().contains("Bob"));
        assertEquals(2, result.metadata().get("resultCount"));
    }

    @Test
    void keysBuiltin() {
        String json = "{\"b\": 2, \"a\": 1, \"c\": 3}";
        ToolResult result = tool.execute(Map.of("json", json, "query", "keys"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("a"));
        assertTrue(result.content().contains("b"));
        assertTrue(result.content().contains("c"));
    }

    @Test
    void lengthBuiltin() {
        String json = "[1, 2, 3, 4]";
        ToolResult result = tool.execute(Map.of("json", json, "query", "length"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("4"));
    }

    @Test
    void typeBuiltin() {
        String json = "{\"key\": \"value\"}";
        ToolResult result = tool.execute(Map.of("json", json, "query", "type"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("object"));
    }

    @Test
    void invalidJsonInput() {
        String json = "{not valid json}";
        ToolResult result = tool.execute(Map.of("json", json, "query", "."), CTX).block();

        assertTrue(result.isError());
        assertTrue(result.content().contains("Failed to read JSON"));
    }

    @Test
    void nonExistentFieldReturnsNull() {
        String json = "{\"existing\": \"value\"}";
        ToolResult result = tool.execute(Map.of("json", json, "query", ".missing"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("null"));
    }

    @Test
    void complexNestedQuery() {
        String json =
                """
                {
                  "users": [
                    {"name": "Alice", "skills": ["java", "python"]},
                    {"name": "Bob", "skills": ["go", "rust"]}
                  ]
                }
                """;
        ToolResult result =
                tool.execute(Map.of("json", json, "query", ".users[0].skills[1]"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("python"));
    }

    @Test
    void arrayExpansionWithoutPipe() {
        String json = "[1, 2, 3]";
        ToolResult result = tool.execute(Map.of("json", json, "query", ".[]"), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("1"));
        assertTrue(result.content().contains("2"));
        assertTrue(result.content().contains("3"));
        assertEquals(3, result.metadata().get("resultCount"));
    }

    @Test
    void missingJsonParameter() {
        ToolResult result = tool.execute(Map.of("query", "."), CTX).block();

        assertTrue(result.isError());
        assertTrue(result.content().contains("'json' is required"));
    }

    @Test
    void missingQueryParameter() {
        ToolResult result = tool.execute(Map.of("json", "{}"), CTX).block();

        assertTrue(result.isError());
        assertTrue(result.content().contains("'query' is required"));
    }

    @Test
    void blankJsonParameter() {
        ToolResult result = tool.execute(Map.of("json", "   ", "query", "."), CTX).block();

        assertTrue(result.isError());
    }

    @Test
    void blankQueryParameter() {
        ToolResult result = tool.execute(Map.of("json", "{}", "query", "  "), CTX).block();

        assertTrue(result.isError());
    }

    @Test
    void queryFromJsonFile() throws IOException {
        String json = "{\"fromFile\": true}";
        Path jsonFile = tempDir.resolve("data.json");
        Files.writeString(jsonFile, json);

        ToolResult result =
                tool.execute(Map.of("json", jsonFile.toString(), "query", ".fromFile"), CTX)
                        .block();

        assertFalse(result.isError(), result.content());
        assertTrue(result.content().contains("true"));
    }

    @Test
    void prettyPrintDisabled() {
        String json = "{\"a\": 1, \"b\": 2}";
        ToolResult result =
                tool.execute(Map.of("json", json, "query", ".", "pretty", false), CTX).block();

        assertFalse(result.isError());
        // Compact output should be on a single line
        assertFalse(result.content().contains("\n"));
    }

    @Test
    void rootDotReturnsFullDocument() {
        String json = "{\"full\": \"document\"}";
        ToolResult result = tool.execute(Map.of("json", json, "query", "."), CTX).block();

        assertFalse(result.isError());
        assertTrue(result.content().contains("full"));
        assertTrue(result.content().contains("document"));
    }
}
