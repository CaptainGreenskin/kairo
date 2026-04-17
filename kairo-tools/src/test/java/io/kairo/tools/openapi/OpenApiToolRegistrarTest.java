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
package io.kairo.tools.openapi;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolRegistry;
import io.kairo.api.tool.ToolSideEffect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiToolRegistrarTest {

    private StubToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StubToolRegistry();
    }

    // ─── JSON spec parsing ──────────────────────────────────────────────

    @Test
    void registerFromString_jsonSpec_registersAllEndpoints() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        // 7 operations: GET /pets, POST /pets, GET /pets/{petId}, PUT /pets/{petId},
        //               DELETE /pets/{petId}, GET /pets/{petId}/toys, GET /store/inventory
        assertEquals(7, tools.size());
        assertEquals(7, registry.registered.size());
    }

    @Test
    void registerFromString_operationIdExtraction() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        assertTrue(byName.containsKey("listPets"));
        assertTrue(byName.containsKey("createPet"));
        assertTrue(byName.containsKey("getPetById"));
        assertTrue(byName.containsKey("updatePet"));
        assertTrue(byName.containsKey("deletePet"));
        assertTrue(byName.containsKey("getInventory"));
    }

    @Test
    void registerFromString_missingOperationIdGeneratesFallback() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        // The GET /pets/{petId}/toys endpoint has no operationId
        Map<String, ToolDefinition> byName = toMap(tools);
        // Should generate something like get_pets_by_petId_toys
        assertTrue(
                byName.containsKey("get_pets_by_petId_toys"),
                "Expected fallback name for GET /pets/{petId}/toys, got: " + byName.keySet());
    }

    @Test
    void registerFromString_sideEffectMappingFromHttpMethod() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        assertEquals(ToolSideEffect.READ_ONLY, byName.get("listPets").sideEffect());
        assertEquals(ToolSideEffect.READ_ONLY, byName.get("getPetById").sideEffect());
        assertEquals(ToolSideEffect.READ_ONLY, byName.get("getInventory").sideEffect());
        assertEquals(ToolSideEffect.WRITE, byName.get("createPet").sideEffect());
        assertEquals(ToolSideEffect.WRITE, byName.get("updatePet").sideEffect());
        assertEquals(ToolSideEffect.WRITE, byName.get("deletePet").sideEffect());
    }

    @Test
    void registerFromString_allToolsAreExternal() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        for (ToolDefinition tool : tools) {
            assertEquals(ToolCategory.EXTERNAL, tool.category());
            assertNull(tool.implementationClass());
        }
    }

    // ─── Parameter merging ──────────────────────────────────────────────

    @Test
    void registerFromString_queryParametersMergedIntoSchema() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        JsonSchema schema = byName.get("listPets").inputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.type());
        assertNotNull(schema.properties());
        assertTrue(schema.properties().containsKey("limit"));
        assertTrue(schema.properties().containsKey("status"));
        // Query params not required
        assertTrue(schema.required() == null || schema.required().isEmpty());
    }

    @Test
    void registerFromString_pathParametersMarkedRequired() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        JsonSchema schema = byName.get("getPetById").inputSchema();
        assertNotNull(schema.properties());
        assertTrue(schema.properties().containsKey("petId"));
        assertTrue(schema.required().contains("petId"));
    }

    @Test
    void registerFromString_requestBodyPropertiesFlattened() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        JsonSchema schema = byName.get("createPet").inputSchema();
        assertNotNull(schema.properties());
        assertTrue(schema.properties().containsKey("name"));
        assertTrue(schema.properties().containsKey("species"));
        assertTrue(schema.properties().containsKey("age"));
        assertTrue(schema.required().contains("name"));
        assertTrue(schema.required().contains("species"));
    }

    @Test
    void registerFromString_pathParamsPlusBodyMerged() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        JsonSchema schema = byName.get("updatePet").inputSchema();
        assertNotNull(schema.properties());
        // path param
        assertTrue(schema.properties().containsKey("petId"));
        // body props
        assertTrue(schema.properties().containsKey("name"));
        assertTrue(schema.properties().containsKey("species"));
        assertTrue(schema.required().contains("petId"));
    }

    // ─── Description and usage guidance ─────────────────────────────────

    @Test
    void registerFromString_descriptionCombinesSummaryAndDescription() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        String desc = byName.get("listPets").description();
        assertTrue(desc.contains("List all pets"));
        assertTrue(desc.contains("Returns all pets in the store"));
    }

    @Test
    void registerFromString_usageGuidanceContainsMethodAndPath() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        assertEquals("HTTP GET /pets", byName.get("listPets").usageGuidance());
        assertEquals("HTTP POST /pets", byName.get("createPet").usageGuidance());
        assertEquals("HTTP DELETE /pets/{petId}", byName.get("deletePet").usageGuidance());
    }

    // ─── YAML format ────────────────────────────────────────────────────

    @Test
    void registerFromString_yamlSpec() {
        String yaml =
                "openapi: '3.0.3'\n"
                        + "info:\n"
                        + "  title: Test\n"
                        + "  version: '1.0'\n"
                        + "paths:\n"
                        + "  /health:\n"
                        + "    get:\n"
                        + "      operationId: healthCheck\n"
                        + "      summary: Health check\n"
                        + "      responses:\n"
                        + "        '200':\n"
                        + "          description: OK\n";
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(yaml, registry);

        assertEquals(1, tools.size());
        assertEquals("healthCheck", tools.get(0).name());
        assertEquals(ToolSideEffect.READ_ONLY, tools.get(0).sideEffect());
    }

    // ─── Edge cases ─────────────────────────────────────────────────────

    @Test
    void registerFromString_emptyPaths_returnsEmpty() {
        String spec =
                "{\"openapi\":\"3.0.3\","
                        + "\"info\":{\"title\":\"Empty\",\"version\":\"1.0\"},"
                        + "\"paths\":{}}";
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);
        assertTrue(tools.isEmpty());
    }

    @Test
    void registerFromString_invalidSpec_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenApiToolRegistrar.registerFromString("not a valid spec", registry));
    }

    @Test
    void registerFromString_noParametersProducesEmptySchema() {
        String spec = loadResourceAsString("/test-openapi.json");
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        Map<String, ToolDefinition> byName = toMap(tools);
        JsonSchema schema = byName.get("getInventory").inputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.type());
        assertTrue(
                schema.properties() == null || schema.properties().isEmpty(),
                "getInventory should have no input parameters");
    }

    @Test
    void registerFromString_complexNestedSchema() {
        String spec =
                "{\"openapi\":\"3.0.3\","
                        + "\"info\":{\"title\":\"Nested\",\"version\":\"1.0\"},"
                        + "\"paths\":{\"/orders\":{\"post\":{"
                        + "\"operationId\":\"createOrder\","
                        + "\"summary\":\"Create order\","
                        + "\"requestBody\":{\"required\":true,\"content\":{\"application/json\":{\"schema\":{"
                        + "\"type\":\"object\","
                        + "\"properties\":{"
                        + "  \"customer\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"email\":{\"type\":\"string\"}},\"required\":[\"name\"]},"
                        + "  \"items\":{\"type\":\"array\",\"description\":\"Order items\"}"
                        + "},"
                        + "\"required\":[\"customer\"]"
                        + "}}}},"
                        + "\"responses\":{\"201\":{\"description\":\"Created\"}}"
                        + "}}}}";
        List<ToolDefinition> tools = OpenApiToolRegistrar.registerFromString(spec, registry);

        assertEquals(1, tools.size());
        ToolDefinition tool = tools.get(0);
        assertEquals("createOrder", tool.name());
        JsonSchema schema = tool.inputSchema();
        assertTrue(schema.properties().containsKey("customer"));
        assertTrue(schema.properties().containsKey("items"));
        assertTrue(schema.required().contains("customer"));

        // Nested object preserved
        JsonSchema customerSchema = schema.properties().get("customer");
        assertEquals("object", customerSchema.type());
        assertNotNull(customerSchema.properties());
        assertTrue(customerSchema.properties().containsKey("name"));
    }

    // ─── Fallback name generation ───────────────────────────────────────

    @Test
    void resolveOperationName_fallbackGeneration() {
        io.swagger.v3.oas.models.Operation op = new io.swagger.v3.oas.models.Operation();
        // no operationId set
        String name =
                OpenApiToolRegistrar.resolveOperationName("get", "/users/{userId}/orders", op);
        assertEquals("get_users_by_userId_orders", name);
    }

    @Test
    void resolveOperationName_usesOperationIdWhenPresent() {
        io.swagger.v3.oas.models.Operation op = new io.swagger.v3.oas.models.Operation();
        op.setOperationId("myCustomOp");
        String name = OpenApiToolRegistrar.resolveOperationName("post", "/anything", op);
        assertEquals("myCustomOp", name);
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private String loadResourceAsString(String resourcePath) {
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, ToolDefinition> toMap(List<ToolDefinition> tools) {
        Map<String, ToolDefinition> map = new java.util.LinkedHashMap<>();
        for (ToolDefinition t : tools) {
            map.put(t.name(), t);
        }
        return map;
    }

    /** Minimal in-memory ToolRegistry for testing. */
    static class StubToolRegistry implements ToolRegistry {
        final List<ToolDefinition> registered = new ArrayList<>();

        @Override
        public void register(ToolDefinition tool) {
            registered.add(tool);
        }

        @Override
        public void unregister(String name) {
            registered.removeIf(t -> t.name().equals(name));
        }

        @Override
        public Optional<ToolDefinition> get(String name) {
            return registered.stream().filter(t -> t.name().equals(name)).findFirst();
        }

        @Override
        public List<ToolDefinition> getByCategory(ToolCategory category) {
            return registered.stream()
                    .filter(t -> t.category() == category)
                    .toList();
        }

        @Override
        public List<ToolDefinition> getAll() {
            return List.copyOf(registered);
        }

        @Override
        public void scan(String... basePackages) {
            // no-op
        }
    }
}
