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
package io.kairo.tools.info;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AskUserToolTest {

    private AskUserTool tool;
    private InputStream originalIn;

    @BeforeEach
    void setUp() {
        tool = new AskUserTool();
        originalIn = System.in;
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
    }

    @Test
    void missingQuestionParameter() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertTrue(result.content().contains("'question' is required"));
    }

    @Test
    void blankQuestionParameter() {
        ToolResult result = tool.execute(Map.of("question", "   "));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'question' is required"));
    }

    @Test
    void freeTextInput() {
        setInput("my answer");
        ToolResult result = tool.execute(Map.of("question", "What is your name?"));
        assertFalse(result.isError());
        assertEquals("my answer", result.content());
        assertEquals("What is your name?", result.metadata().get("question"));
    }

    @Test
    void emptyInputReturnsNoResponse() {
        setInput("");
        ToolResult result = tool.execute(Map.of("question", "What is your name?"));
        assertFalse(result.isError());
        assertEquals("(no response)", result.content());
    }

    @Test
    void blankInputReturnsNoResponse() {
        setInput("   ");
        ToolResult result = tool.execute(Map.of("question", "What is your name?"));
        assertFalse(result.isError());
        assertEquals("(no response)", result.content());
    }

    @Test
    void optionSelectionByNumber() {
        setInput("2");
        ToolResult result =
                tool.execute(
                        Map.of(
                                "question",
                                "Choose a color",
                                "options",
                                List.of("Red", "Green", "Blue")));
        assertFalse(result.isError());
        assertEquals("Green", result.content());
        assertEquals(2, result.metadata().get("selectedIndex"));
        assertEquals("Choose a color", result.metadata().get("question"));
    }

    @Test
    void optionSelectionFirstOption() {
        setInput("1");
        ToolResult result =
                tool.execute(
                        Map.of("question", "Pick one", "options", List.of("Option A", "Option B")));
        assertFalse(result.isError());
        assertEquals("Option A", result.content());
        assertEquals(1, result.metadata().get("selectedIndex"));
    }

    @Test
    void optionSelectionLastOption() {
        setInput("3");
        ToolResult result =
                tool.execute(Map.of("question", "Pick one", "options", List.of("A", "B", "C")));
        assertFalse(result.isError());
        assertEquals("C", result.content());
        assertEquals(3, result.metadata().get("selectedIndex"));
    }

    @Test
    void optionNumberOutOfRangeReturnsCustomText() {
        setInput("5");
        ToolResult result =
                tool.execute(Map.of("question", "Pick one", "options", List.of("A", "B", "C")));
        assertFalse(result.isError());
        assertEquals("5", result.content());
        assertNull(result.metadata().get("selectedIndex"));
    }

    @Test
    void optionZeroOutOfRangeReturnsCustomText() {
        setInput("0");
        ToolResult result =
                tool.execute(Map.of("question", "Pick one", "options", List.of("A", "B", "C")));
        assertFalse(result.isError());
        assertEquals("0", result.content());
    }

    @Test
    void customTextWhenOptionsProvided() {
        setInput("custom text");
        ToolResult result =
                tool.execute(Map.of("question", "Pick one", "options", List.of("A", "B")));
        assertFalse(result.isError());
        assertEquals("custom text", result.content());
        assertNull(result.metadata().get("selectedIndex"));
    }

    @Test
    void emptyOptionsListActsAsFreeText() {
        setInput("my answer");
        ToolResult result = tool.execute(Map.of("question", "Name?", "options", List.of()));
        assertFalse(result.isError());
        assertEquals("my answer", result.content());
    }

    @Test
    void inputIsTrimmed() {
        setInput("  hello world  ");
        ToolResult result = tool.execute(Map.of("question", "Say hi"));
        assertFalse(result.isError());
        assertEquals("hello world", result.content());
    }

    @Test
    void errorResultFormat() {
        ToolResult result = tool.execute(Map.of());
        assertTrue(result.isError());
        assertEquals("ask_user", result.toolUseId());
    }

    @Test
    void ioExceptionReturnsError() {
        // Set System.in to an InputStream that throws on read
        System.setIn(
                new InputStream() {
                    @Override
                    public int read() {
                        throw new RuntimeException("Simulated IO error");
                    }
                });
        ToolResult result = tool.execute(Map.of("question", "test?"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Failed to read user input"));
    }

    private void setInput(String text) {
        System.setIn(new ByteArrayInputStream((text + "\n").getBytes()));
    }
}
