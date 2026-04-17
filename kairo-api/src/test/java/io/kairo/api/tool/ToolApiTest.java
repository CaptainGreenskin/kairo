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
package io.kairo.api.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolApiTest {

    // --- ApprovalResult ---

    @Test
    void approvalResultAllow() {
        ApprovalResult result = ApprovalResult.allow();
        assertTrue(result.approved());
        assertNull(result.reason());
    }

    @Test
    void approvalResultDenied() {
        ApprovalResult result = ApprovalResult.denied("unsafe command");
        assertFalse(result.approved());
        assertEquals("unsafe command", result.reason());
    }

    // --- ToolCallRequest ---

    @Test
    void toolCallRequestFields() {
        Map<String, Object> args = Map.of("path", "/tmp/test");
        ToolCallRequest req = new ToolCallRequest("write_file", args, ToolSideEffect.WRITE);

        assertEquals("write_file", req.toolName());
        assertEquals(args, req.args());
        assertEquals(ToolSideEffect.WRITE, req.sideEffect());
    }

    // --- ToolDefinition ---

    @Test
    void toolDefinitionFullConstructor() {
        JsonSchema schema = new JsonSchema("object", null, null, "test schema");
        Duration timeout = Duration.ofSeconds(30);
        ToolDefinition def =
                new ToolDefinition(
                        "bash",
                        "Execute shell",
                        ToolCategory.EXECUTION,
                        schema,
                        String.class,
                        timeout,
                        ToolSideEffect.SYSTEM_CHANGE);

        assertEquals("bash", def.name());
        assertEquals("Execute shell", def.description());
        assertEquals(ToolCategory.EXECUTION, def.category());
        assertEquals(schema, def.inputSchema());
        assertEquals(String.class, def.implementationClass());
        assertEquals(timeout, def.timeout());
        assertEquals(ToolSideEffect.SYSTEM_CHANGE, def.sideEffect());
    }

    @Test
    void toolDefinitionBackwardCompatNoTimeoutNoSideEffect() {
        JsonSchema schema = new JsonSchema("object", null, null, null);
        ToolDefinition def =
                new ToolDefinition(
                        "read", "Read file", ToolCategory.FILE_AND_CODE, schema, String.class);

        assertNull(def.timeout());
        assertEquals(ToolSideEffect.READ_ONLY, def.sideEffect());
    }

    @Test
    void toolDefinitionBackwardCompatWithTimeout() {
        JsonSchema schema = new JsonSchema("object", null, null, null);
        Duration timeout = Duration.ofMinutes(1);
        ToolDefinition def =
                new ToolDefinition(
                        "search",
                        "Search files",
                        ToolCategory.FILE_AND_CODE,
                        schema,
                        String.class,
                        timeout);

        assertEquals(timeout, def.timeout());
        assertEquals(ToolSideEffect.READ_ONLY, def.sideEffect());
    }

    // --- ToolResult ---

    @Test
    void toolResultFields() {
        Map<String, Object> meta = Map.of("elapsed", 100);
        ToolResult result = new ToolResult("tu-1", "file content", false, meta);

        assertEquals("tu-1", result.toolUseId());
        assertEquals("file content", result.content());
        assertFalse(result.isError());
        assertEquals(meta, result.metadata());
    }

    @Test
    void toolResultError() {
        ToolResult result = new ToolResult("tu-2", "file not found", true, null);
        assertTrue(result.isError());
        assertNull(result.metadata());
    }

    // --- ToolInvocation ---

    @Test
    void toolInvocationFields() {
        Map<String, Object> input = Map.of("query", "test");
        ToolInvocation inv = new ToolInvocation("search", input);
        assertEquals("search", inv.toolName());
        assertEquals(input, inv.input());
    }

    // --- JsonSchema ---

    @Test
    void jsonSchemaFields() {
        JsonSchema nested = new JsonSchema("string", null, null, "a path");
        JsonSchema schema =
                new JsonSchema("object", Map.of("path", nested), List.of("path"), "input schema");

        assertEquals("object", schema.type());
        assertEquals(1, schema.properties().size());
        assertEquals(List.of("path"), schema.required());
        assertEquals("input schema", schema.description());
    }

    // --- StreamingToolResultCallback ---

    @Test
    void noopCallbackDoesNotThrow() {
        StreamingToolResultCallback noop = StreamingToolResultCallback.noop();
        assertNotNull(noop);
        assertDoesNotThrow(() -> noop.onPartialOutput("id", "chunk"));
        assertDoesNotThrow(() -> noop.onComplete("id", new ToolResult("id", "done", false, null)));
    }

    // --- Enums ---

    @Test
    void toolSideEffectValues() {
        ToolSideEffect[] values = ToolSideEffect.values();
        assertEquals(3, values.length);
        assertNotNull(ToolSideEffect.valueOf("READ_ONLY"));
        assertNotNull(ToolSideEffect.valueOf("WRITE"));
        assertNotNull(ToolSideEffect.valueOf("SYSTEM_CHANGE"));
    }

    @Test
    void toolPermissionValues() {
        ToolPermission[] values = ToolPermission.values();
        assertEquals(3, values.length);
        assertNotNull(ToolPermission.valueOf("ALLOWED"));
        assertNotNull(ToolPermission.valueOf("ASK"));
        assertNotNull(ToolPermission.valueOf("DENIED"));
    }

    @Test
    void toolCategoryValues() {
        ToolCategory[] values = ToolCategory.values();
        assertEquals(9, values.length);
        assertNotNull(ToolCategory.valueOf("FILE_AND_CODE"));
        assertNotNull(ToolCategory.valueOf("EXECUTION"));
        assertNotNull(ToolCategory.valueOf("INFORMATION"));
        assertNotNull(ToolCategory.valueOf("AGENT_AND_TASK"));
        assertNotNull(ToolCategory.valueOf("WORKSPACE"));
        assertNotNull(ToolCategory.valueOf("SCHEDULING"));
        assertNotNull(ToolCategory.valueOf("SKILL"));
        assertNotNull(ToolCategory.valueOf("GENERAL"));
        assertNotNull(ToolCategory.valueOf("EXTERNAL"));
    }
}
