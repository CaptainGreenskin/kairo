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
package io.kairo.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnnotationToolScannerTest {

    private final AnnotationToolScanner scanner = new AnnotationToolScanner();

    @Tool(name = "test_tool", description = "A test tool", category = ToolCategory.GENERAL)
    static class TestToolWithParams implements ToolHandler {
        @ToolParam(description = "The file path", required = true)
        String path;

        @ToolParam(description = "Line number")
        int lineNumber;

        @ToolParam(description = "Verbose flag")
        boolean verbose;

        @Override
        public ToolResult execute(Map<String, Object> input) {
            return new ToolResult("test", "done", false, Map.of());
        }
    }

    @Tool(name = "no_params", description = "Tool without params")
    static class NoParamsTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            return new ToolResult("test", "done", false, Map.of());
        }
    }

    @Tool(name = "timed_tool", description = "Tool with custom timeout", timeoutSeconds = 30)
    static class TimedTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            return new ToolResult("test", "done", false, Map.of());
        }
    }

    @Tool(
            name = "guided_tool",
            description = "Tool with usage guidance",
            usageGuidance = "Use for quick reads; for large files use GrepTool instead")
    static class GuidedTool implements ToolHandler {
        @Override
        public ToolResult execute(Map<String, Object> input) {
            return new ToolResult("test", "done", false, Map.of());
        }
    }

    @Test
    void scanClassExtractsAnnotationMetadata() {
        ToolDefinition def = scanner.scanClass(TestToolWithParams.class);
        assertEquals("test_tool", def.name());
        assertEquals("A test tool", def.description());
        assertEquals(ToolCategory.GENERAL, def.category());
        assertSame(TestToolWithParams.class, def.implementationClass());
    }

    @Test
    void scanClassBuildsSchemaFromToolParams() {
        ToolDefinition def = scanner.scanClass(TestToolWithParams.class);
        JsonSchema schema = def.inputSchema();

        assertEquals("object", schema.type());
        assertNotNull(schema.properties());
        assertEquals(3, schema.properties().size());

        // Check path param
        JsonSchema pathSchema = schema.properties().get("path");
        assertEquals("string", pathSchema.type());
        assertEquals("The file path", pathSchema.description());

        // Check lineNumber param
        JsonSchema lineSchema = schema.properties().get("lineNumber");
        assertEquals("integer", lineSchema.type());

        // Check verbose param
        JsonSchema verboseSchema = schema.properties().get("verbose");
        assertEquals("boolean", verboseSchema.type());

        // Check required
        assertEquals(1, schema.required().size());
        assertTrue(schema.required().contains("path"));
    }

    @Test
    void scanClassWithNoParamsHasEmptySchema() {
        ToolDefinition def = scanner.scanClass(NoParamsTool.class);
        JsonSchema schema = def.inputSchema();
        assertEquals("object", schema.type());
        assertTrue(schema.properties().isEmpty());
        assertTrue(schema.required().isEmpty());
    }

    @Test
    void scanClassThrowsForNonAnnotatedClass() {
        assertThrows(IllegalArgumentException.class, () -> scanner.scanClass(String.class));
    }

    @Test
    void scanClassHandlesDefaultCategory() {
        ToolDefinition def = scanner.scanClass(NoParamsTool.class);
        assertEquals(ToolCategory.GENERAL, def.category());
    }

    @Test
    void scanClassExtractsCustomTimeout() {
        ToolDefinition def = scanner.scanClass(TimedTool.class);
        assertNotNull(def.timeout());
        assertEquals(Duration.ofSeconds(30), def.timeout());
    }

    @Test
    void scanClassDefaultTimeoutIsNull() {
        ToolDefinition def = scanner.scanClass(NoParamsTool.class);
        assertNull(def.timeout());
    }

    @Test
    void toolDefinitionBackwardCompatibleConstructor() {
        ToolDefinition def =
                new ToolDefinition(
                        "legacy",
                        "legacy tool",
                        ToolCategory.GENERAL,
                        new JsonSchema("object", null, null, null),
                        Object.class);
        assertNull(def.timeout());
        assertEquals("legacy", def.name());
        assertEquals("", def.usageGuidance());
    }

    @Test
    void scanClassExtractsUsageGuidance() {
        ToolDefinition def = scanner.scanClass(GuidedTool.class);
        assertEquals("guided_tool", def.name());
        assertEquals(
                "Use for quick reads; for large files use GrepTool instead",
                def.usageGuidance());
    }

    @Test
    void scanClassDefaultUsageGuidanceIsEmpty() {
        ToolDefinition def = scanner.scanClass(NoParamsTool.class);
        assertEquals("", def.usageGuidance());
    }
}
