/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.model.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import org.junit.jupiter.api.Test;

class GeminiResponseParserTest {

    private final GeminiResponseParser parser = new GeminiResponseParser(new ObjectMapper());

    @Test
    void parsesPlainTextResponse() throws Exception {
        String body =
                """
                {
                  "candidates": [{
                    "content": {"parts": [{"text": "Hello there"}], "role": "model"},
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {"promptTokenCount": 5, "candidatesTokenCount": 3}
                }
                """;
        ModelResponse r = parser.parse(body, "gemini-2.0-flash");
        assertThat(r.contents()).hasSize(1);
        assertThat(r.contents().get(0)).isInstanceOf(Content.TextContent.class);
        assertThat(((Content.TextContent) r.contents().get(0)).text()).isEqualTo("Hello there");
        assertThat(r.stopReason()).isEqualTo(ModelResponse.StopReason.END_TURN);
        assertThat(r.usage().inputTokens()).isEqualTo(5);
        assertThat(r.usage().outputTokens()).isEqualTo(3);
        assertThat(r.model()).isEqualTo("gemini-2.0-flash");
    }

    @Test
    void parsesFunctionCall() throws Exception {
        String body =
                """
                {
                  "candidates": [{
                    "content": {"parts": [{
                      "functionCall": {"name": "get_weather", "args": {"city": "Paris"}}
                    }]},
                    "finishReason": "STOP"
                  }]
                }
                """;
        ModelResponse r = parser.parse(body, "gemini-2.0-flash");
        assertThat(r.contents()).hasSize(1);
        Content.ToolUseContent tu = (Content.ToolUseContent) r.contents().get(0);
        assertThat(tu.toolName()).isEqualTo("get_weather");
        assertThat(tu.input()).containsEntry("city", "Paris");
        assertThat(tu.toolId()).startsWith("get_weather-");
    }

    @Test
    void parsesMixedTextAndFunctionCall() throws Exception {
        String body =
                """
                {
                  "candidates": [{
                    "content": {"parts": [
                      {"text": "Let me check"},
                      {"functionCall": {"name": "search", "args": {"q": "x"}}}
                    ]}
                  }]
                }
                """;
        ModelResponse r = parser.parse(body, "gemini-2.0-flash");
        assertThat(r.contents()).hasSize(2);
        assertThat(r.contents().get(0)).isInstanceOf(Content.TextContent.class);
        assertThat(r.contents().get(1)).isInstanceOf(Content.ToolUseContent.class);
    }

    @Test
    void mapsMaxTokensFinishReason() throws Exception {
        String body =
                """
                {"candidates":[{"content":{"parts":[{"text":"hi"}]},"finishReason":"MAX_TOKENS"}]}
                """;
        ModelResponse r = parser.parse(body, "gemini-2.0-flash");
        assertThat(r.stopReason()).isEqualTo(ModelResponse.StopReason.MAX_TOKENS);
    }

    @Test
    void handlesEmptyCandidates() throws Exception {
        String body = "{\"candidates\": []}";
        ModelResponse r = parser.parse(body, "gemini-2.0-flash");
        assertThat(r.contents()).isEmpty();
        assertThat(r.stopReason()).isNull();
    }
}
