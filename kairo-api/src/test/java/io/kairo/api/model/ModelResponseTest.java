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
package io.kairo.api.model;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.message.Content;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelResponseTest {

    @Test
    void constructionAndFieldAccess() {
        ModelResponse.Usage usage = new ModelResponse.Usage(100, 50, 10, 5);
        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent("hello")),
                        usage,
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet");

        assertEquals("resp-1", response.id());
        assertEquals(1, response.contents().size());
        assertEquals(usage, response.usage());
        assertEquals(ModelResponse.StopReason.END_TURN, response.stopReason());
        assertEquals("claude-sonnet", response.model());
    }

    @Test
    void usageFieldAccess() {
        ModelResponse.Usage usage = new ModelResponse.Usage(500, 200, 100, 50);
        assertEquals(500, usage.inputTokens());
        assertEquals(200, usage.outputTokens());
        assertEquals(100, usage.cacheReadTokens());
        assertEquals(50, usage.cacheCreationTokens());
    }

    @Test
    void stopReasonValues() {
        ModelResponse.StopReason[] values = ModelResponse.StopReason.values();
        assertEquals(4, values.length);
        assertNotNull(ModelResponse.StopReason.valueOf("END_TURN"));
        assertNotNull(ModelResponse.StopReason.valueOf("TOOL_USE"));
        assertNotNull(ModelResponse.StopReason.valueOf("MAX_TOKENS"));
        assertNotNull(ModelResponse.StopReason.valueOf("STOP_SEQUENCE"));
    }

    @Test
    void contentAsDeserializesJson() {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        ModelResponse response =
                new ModelResponse(
                        "r1",
                        List.of(new Content.TextContent(json)),
                        new ModelResponse.Usage(0, 0, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "model");

        TestPojo result = response.contentAs(TestPojo.class);
        assertEquals("Alice", result.name);
        assertEquals(30, result.age);
    }

    @Test
    void contentAsThrowsOnNoTextContent() {
        ModelResponse response =
                new ModelResponse(
                        "r1",
                        List.of(),
                        new ModelResponse.Usage(0, 0, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "model");

        assertThrows(IllegalStateException.class, () -> response.contentAs(TestPojo.class));
    }

    @Test
    void contentAsThrowsOnInvalidJson() {
        ModelResponse response =
                new ModelResponse(
                        "r1",
                        List.of(new Content.TextContent("not json")),
                        new ModelResponse.Usage(0, 0, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "model");

        assertThrows(IllegalStateException.class, () -> response.contentAs(TestPojo.class));
    }

    @Test
    void contentAsOptionalReturnsOnSuccess() {
        String json = "{\"name\":\"Bob\",\"age\":25}";
        ModelResponse response =
                new ModelResponse(
                        "r1",
                        List.of(new Content.TextContent(json)),
                        new ModelResponse.Usage(0, 0, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "model");

        Optional<TestPojo> result = response.contentAsOptional(TestPojo.class);
        assertTrue(result.isPresent());
        assertEquals("Bob", result.get().name);
    }

    @Test
    void contentAsOptionalReturnsEmptyOnFailure() {
        ModelResponse response =
                new ModelResponse(
                        "r1",
                        List.of(new Content.TextContent("invalid")),
                        new ModelResponse.Usage(0, 0, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "model");

        Optional<TestPojo> result = response.contentAsOptional(TestPojo.class);
        assertTrue(result.isEmpty());
    }

    public static class TestPojo {
        public String name;
        public int age;
    }
}
