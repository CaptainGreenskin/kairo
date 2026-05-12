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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ToolResult v1.2 factory methods and backward compatibility")
class ToolResultTest {

    @Test
    @DisplayName("success(id, content) creates SUCCESS outcome with Text output")
    void successFactory_simple() {
        ToolResult r = ToolResult.success("tu-1", "hello");

        assertEquals("tu-1", r.toolUseId());
        assertEquals(ToolOutcome.SUCCESS, r.outcome());
        assertInstanceOf(ToolOutput.Text.class, r.output());
        assertEquals("hello", r.content());
        assertFalse(r.isError());
        assertEquals(List.of(), r.hints());
        assertEquals(Map.of(), r.metadata());
    }

    @Test
    @DisplayName("success(id, content, metadata) preserves metadata")
    void successFactory_withMetadata() {
        Map<String, Object> meta = Map.of("elapsed", 42);
        ToolResult r = ToolResult.success("tu-2", "data", meta);

        assertEquals("tu-2", r.toolUseId());
        assertFalse(r.isError());
        assertEquals(meta, r.metadata());
    }

    @Test
    @DisplayName("error(id, message) creates ERROR outcome")
    void errorFactory_simple() {
        ToolResult r = ToolResult.error("tu-3", "not found");

        assertEquals("tu-3", r.toolUseId());
        assertEquals(ToolOutcome.ERROR, r.outcome());
        assertTrue(r.isError());
        assertEquals("not found", r.content());
    }

    @Test
    @DisplayName("error(id, message, hints) attaches hints")
    void errorFactory_withHints() {
        Hint hint = new Hint(Hint.HintLevel.WARNING, "try again", java.util.Optional.of("retry"));
        ToolResult r = ToolResult.error("tu-4", "timeout", List.of(hint));

        assertTrue(r.isError());
        assertEquals(1, r.hints().size());
        assertEquals("try again", r.hints().get(0).message());
    }

    @Test
    @DisplayName("error(id, message, metadata) preserves metadata")
    void errorFactory_withMetadata() {
        Map<String, Object> meta = Map.of("code", 404);
        ToolResult r = ToolResult.error("tu-5", "missing", meta);

        assertTrue(r.isError());
        assertEquals(meta, r.metadata());
    }

    @Test
    @DisplayName("deprecated of() factory bridges old 4-arg pattern")
    @SuppressWarnings("deprecation")
    void ofFactory_success() {
        ToolResult r = ToolResult.of("tu-6", "ok", false, Map.of("k", "v"));

        assertFalse(r.isError());
        assertEquals("ok", r.content());
        assertEquals(Map.of("k", "v"), r.metadata());
    }

    @Test
    @DisplayName("deprecated of() with null metadata defaults to empty map")
    @SuppressWarnings("deprecation")
    void ofFactory_nullMetadata() {
        ToolResult r = ToolResult.of("tu-7", "fail", true, null);

        assertTrue(r.isError());
        assertEquals(Map.of(), r.metadata());
    }

    @Test
    @DisplayName("content() returns text for Text output")
    void content_textOutput() {
        ToolResult r = ToolResult.success("id", "hello world");
        assertEquals("hello world", r.content());
    }

    @Test
    @DisplayName("content() returns toString for non-Text output")
    void content_nonTextOutput() {
        ToolOutput.Structured structured = new ToolOutput.Structured(Map.of("key", "val"));
        ToolResult r = new ToolResult("id", structured, ToolOutcome.SUCCESS, List.of(), Map.of());
        // Should not throw, returns a string representation
        assertNotNull(r.content());
    }

    @Test
    @DisplayName("isError() reflects outcome classification correctly")
    void isError_reflectsOutcome() {
        ToolResult success = ToolResult.success("id", "ok");
        ToolResult error = ToolResult.error("id", "bad");
        ToolResult cancelled =
                new ToolResult(
                        "id",
                        new ToolOutput.Text("cancelled"),
                        ToolOutcome.CANCELLED,
                        List.of(),
                        Map.of());
        ToolResult timeout =
                new ToolResult(
                        "id",
                        new ToolOutput.Text("timed out"),
                        ToolOutcome.TIMEOUT,
                        List.of(),
                        Map.of());

        assertFalse(success.isError());
        assertTrue(error.isError());
        assertTrue(cancelled.isError());
        assertTrue(timeout.isError());
    }
}
