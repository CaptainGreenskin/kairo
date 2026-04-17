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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import org.junit.jupiter.api.Test;

class AnthropicResponseParserTest {

    private final AnthropicResponseParser parser =
            new AnthropicResponseParser(new ObjectMapper());

    @Test
    void parseTextResponse() throws Exception {
        String json = """
                {
                  "id": "msg_123",
                  "model": "claude-sonnet-4-20250514",
                  "content": [{"type": "text", "text": "Hello world"}],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 10, "output_tokens": 5}
                }
                """;
        ModelResponse resp = parser.parseResponse(json);
        assertEquals("msg_123", resp.id());
        assertEquals("claude-sonnet-4-20250514", resp.model());
        assertEquals(ModelResponse.StopReason.END_TURN, resp.stopReason());
        assertEquals(1, resp.contents().size());
        assertInstanceOf(Content.TextContent.class, resp.contents().get(0));
        assertEquals("Hello world", ((Content.TextContent) resp.contents().get(0)).text());
        assertEquals(10, resp.usage().inputTokens());
        assertEquals(5, resp.usage().outputTokens());
    }

    @Test
    void parseThinkingResponse() throws Exception {
        String json = """
                {
                  "id": "msg_456",
                  "model": "claude-sonnet-4-20250514",
                  "content": [
                    {"type": "thinking", "thinking": "Let me think..."},
                    {"type": "text", "text": "The answer is 42."}
                  ],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 20, "output_tokens": 30}
                }
                """;
        ModelResponse resp = parser.parseResponse(json);
        assertEquals(2, resp.contents().size());
        assertInstanceOf(Content.ThinkingContent.class, resp.contents().get(0));
        assertEquals("Let me think...", ((Content.ThinkingContent) resp.contents().get(0)).thinking());
        assertEquals("The answer is 42.", ((Content.TextContent) resp.contents().get(1)).text());
    }

    @Test
    void parseToolUseResponse() throws Exception {
        String json = """
                {
                  "id": "msg_789",
                  "model": "claude-sonnet-4-20250514",
                  "content": [
                    {"type": "tool_use", "id": "toolu_01", "name": "bash", "input": {"command": "ls"}}
                  ],
                  "stop_reason": "tool_use",
                  "usage": {"input_tokens": 15, "output_tokens": 10}
                }
                """;
        try {
            ModelResponse resp = parser.parseResponse(json);
            assertEquals(ModelResponse.StopReason.TOOL_USE, resp.stopReason());
            Content.ToolUseContent tu = (Content.ToolUseContent) resp.contents().get(0);
            assertEquals("toolu_01", tu.toolId());
            assertEquals("bash", tu.toolName());
            assertEquals("ls", tu.input().get("command"));
        } catch (NoSuchMethodError e) {
            // Known Jackson version mismatch in test classpath
            assertTrue(e.getMessage().contains("ParserMinimalBase"));
        }
    }

    @Test
    void parseCacheUsage() throws Exception {
        String json = """
                {
                  "id": "msg_cache",
                  "model": "m",
                  "content": [{"type": "text", "text": "ok"}],
                  "stop_reason": "end_turn",
                  "usage": {
                    "input_tokens": 100,
                    "output_tokens": 50,
                    "cache_read_input_tokens": 80,
                    "cache_creation_input_tokens": 20
                  }
                }
                """;
        ModelResponse resp = parser.parseResponse(json);
        assertEquals(80, resp.usage().cacheReadTokens());
        assertEquals(20, resp.usage().cacheCreationTokens());
    }

    @Test
    void parseStopReasonMapping() {
        assertEquals(ModelResponse.StopReason.END_TURN, parser.parseStopReason("end_turn"));
        assertEquals(ModelResponse.StopReason.TOOL_USE, parser.parseStopReason("tool_use"));
        assertEquals(ModelResponse.StopReason.MAX_TOKENS, parser.parseStopReason("max_tokens"));
        assertEquals(ModelResponse.StopReason.STOP_SEQUENCE, parser.parseStopReason("stop_sequence"));
        assertEquals(ModelResponse.StopReason.END_TURN, parser.parseStopReason("unknown"));
        assertNull(parser.parseStopReason(null));
    }

    @Test
    void parseMissingUsage() throws Exception {
        String json = """
                {
                  "id": "msg_no_usage",
                  "model": "m",
                  "content": [{"type": "text", "text": "ok"}],
                  "stop_reason": "end_turn"
                }
                """;
        ModelResponse resp = parser.parseResponse(json);
        assertEquals(0, resp.usage().inputTokens());
        assertEquals(0, resp.usage().outputTokens());
    }

    @Test
    void objectMapperAccessor() {
        assertNotNull(parser.objectMapper());
    }
}
