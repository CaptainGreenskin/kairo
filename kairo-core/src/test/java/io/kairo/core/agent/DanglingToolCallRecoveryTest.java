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
package io.kairo.core.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for dangling tool call recovery in {@link ReActLoop}.
 *
 * <p>Verifies that when conversation history ends with an ASSISTANT message containing tool_use
 * blocks but no corresponding TOOL result messages, the loop injects error ToolResults before
 * starting the first iteration.
 */
class DanglingToolCallRecoveryTest {

    private ModelProvider modelProvider;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        toolExecutor = mock(ToolExecutor.class);
    }

    private ReActLoop createLoop() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();

        ErrorRecoveryStrategy errorRecovery =
                new ErrorRecoveryStrategy(modelProvider, null, new ModelFallbackManager(List.of()));

        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-1",
                        "test-agent",
                        config,
                        new DefaultHookChain(),
                        null, // tracer
                        toolExecutor,
                        errorRecovery,
                        new TokenBudgetManager(200_000, 8_096),
                        new GracefulShutdownManager(),
                        null, // contextManager
                        null); // guardrailChain

        ModelConfig modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        return new ReActLoop(
                ctx,
                new AtomicBoolean(false),
                new AtomicInteger(0),
                new AtomicLong(0),
                () -> modelConfig);
    }

    // ===== 1. Detect dangling calls and inject error ToolResult =====

    @Test
    void testDanglingToolCallInjectsErrorResult() {
        ReActLoop loop = createLoop();

        // Inject: USER message, then ASSISTANT with tool_use, but NO TOOL result
        Msg userMsg = Msg.of(MsgRole.USER, "do something");
        Msg assistantMsg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .toolUse("tc-dangling", "search", Map.of("q", "test"))
                        .build();

        loop.injectMessages(List.of(userMsg, assistantMsg));

        loop.recoverDanglingToolCalls();

        List<Msg> history = loop.getHistory();
        assertEquals(3, history.size(), "Should have USER, ASSISTANT, and injected TOOL message");

        Msg toolMsg = history.get(2);
        assertEquals(MsgRole.TOOL, toolMsg.role());
        assertEquals(1, toolMsg.contents().size());

        Content content = toolMsg.contents().get(0);
        assertInstanceOf(Content.ToolResultContent.class, content);
        Content.ToolResultContent trc = (Content.ToolResultContent) content;
        assertEquals("tc-dangling", trc.toolUseId());
        assertTrue(trc.isError());
        assertTrue(trc.content().contains("interrupted"));
    }

    // ===== 2. No false positives on complete tool_call → tool_result pairs =====

    @Test
    void testNoFalsePositiveForCompleteToolCallPair() {
        ReActLoop loop = createLoop();

        Msg userMsg = Msg.of(MsgRole.USER, "do something");
        Msg assistantMsg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .toolUse("tc-1", "search", Map.of("q", "hello"))
                        .build();
        Msg toolMsg =
                MsgBuilder.create()
                        .role(MsgRole.TOOL)
                        .addToolResult("tc-1", "found 3 results", false)
                        .build();

        loop.injectMessages(List.of(userMsg, assistantMsg, toolMsg));

        loop.recoverDanglingToolCalls();

        List<Msg> history = loop.getHistory();
        assertEquals(3, history.size(), "No new messages should be added");
    }

    // ===== 3. Multiple dangling calls in single ASSISTANT message =====

    @Test
    void testMultipleDanglingCallsInSingleAssistant() {
        ReActLoop loop = createLoop();

        Msg userMsg = Msg.of(MsgRole.USER, "do two things");
        Msg assistantMsg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .toolUse("tc-a", "read_file", Map.of("path", "a.txt"))
                        .toolUse("tc-b", "read_file", Map.of("path", "b.txt"))
                        .build();

        loop.injectMessages(List.of(userMsg, assistantMsg));

        loop.recoverDanglingToolCalls();

        List<Msg> history = loop.getHistory();
        assertEquals(3, history.size());

        Msg injected = history.get(2);
        assertEquals(MsgRole.TOOL, injected.role());
        assertEquals(2, injected.contents().size(), "Should have 2 error ToolResults");

        // Verify both dangling IDs are covered
        List<String> recoveredIds =
                injected.contents().stream()
                        .filter(Content.ToolResultContent.class::isInstance)
                        .map(Content.ToolResultContent.class::cast)
                        .map(Content.ToolResultContent::toolUseId)
                        .toList();
        assertTrue(recoveredIds.contains("tc-a"));
        assertTrue(recoveredIds.contains("tc-b"));

        // Verify all are errors
        injected.contents().stream()
                .filter(Content.ToolResultContent.class::isInstance)
                .map(Content.ToolResultContent.class::cast)
                .forEach(trc -> assertTrue(trc.isError()));
    }

    // ===== 4. Session resumption: history loaded with dangling calls =====

    @Test
    void testSessionResumptionWithDanglingCalls() {
        ReActLoop loop = createLoop();

        // Simulate a session that was interrupted mid-execution:
        // USER → ASSISTANT(tool_use tc-1, tc-2) → TOOL(tc-1 only) — tc-2 is dangling
        Msg userMsg = Msg.of(MsgRole.USER, "multi-tool task");
        Msg assistantMsg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .toolUse("tc-1", "search", Map.of("q", "a"))
                        .toolUse("tc-2", "write_file", Map.of("path", "out.txt"))
                        .build();
        Msg partialTool =
                MsgBuilder.create()
                        .role(MsgRole.TOOL)
                        .addToolResult("tc-1", "search results", false)
                        .build();

        loop.injectMessages(List.of(userMsg, assistantMsg, partialTool));

        loop.recoverDanglingToolCalls();

        List<Msg> history = loop.getHistory();
        assertEquals(4, history.size(), "Should inject one TOOL message for the dangling tc-2");

        Msg injected = history.get(3);
        assertEquals(MsgRole.TOOL, injected.role());
        assertEquals(1, injected.contents().size());

        Content.ToolResultContent trc = (Content.ToolResultContent) injected.contents().get(0);
        assertEquals("tc-2", trc.toolUseId());
        assertTrue(trc.isError());
    }

    // ===== 5. No dangling calls: history with no tool calls =====

    @Test
    void testNoDanglingCallsWithTextOnlyAssistant() {
        ReActLoop loop = createLoop();

        Msg userMsg = Msg.of(MsgRole.USER, "hello");
        Msg assistantMsg = Msg.of(MsgRole.ASSISTANT, "Hi there!");

        loop.injectMessages(List.of(userMsg, assistantMsg));

        loop.recoverDanglingToolCalls();

        List<Msg> history = loop.getHistory();
        assertEquals(2, history.size(), "No modification expected");
    }

    // ===== 6. Empty history =====

    @Test
    void testEmptyHistoryNoError() {
        ReActLoop loop = createLoop();

        // Should not throw on empty history
        assertDoesNotThrow(() -> loop.recoverDanglingToolCalls());
        assertTrue(loop.getHistory().isEmpty());
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    void testErrorMessageContainsInterrupted() {
        ReActLoop loop = createLoop();

        Msg userMsg = Msg.of(MsgRole.USER, "do something");
        Msg assistantMsg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .toolUse("tc-check", "read_file", Map.of("path", "test.txt"))
                        .build();

        loop.injectMessages(List.of(userMsg, assistantMsg));
        loop.recoverDanglingToolCalls();

        List<Msg> history = loop.getHistory();
        Msg injected = history.get(2);
        Content.ToolResultContent trc = (Content.ToolResultContent) injected.contents().get(0);

        // Verify the error message contains "interrupted"
        assertTrue(
                trc.content().toLowerCase().contains("interrupted"),
                "Error message should contain 'interrupted' but was: " + trc.content());
    }

    @Test
    void testRecoveredToolUseIdMatchesDanglingCall() {
        ReActLoop loop = createLoop();

        String danglingId = "tc-unique-12345";
        Msg userMsg = Msg.of(MsgRole.USER, "run task");
        Msg assistantMsg =
                MsgBuilder.create()
                        .role(MsgRole.ASSISTANT)
                        .toolUse(danglingId, "execute", Map.of("cmd", "ls"))
                        .build();

        loop.injectMessages(List.of(userMsg, assistantMsg));
        loop.recoverDanglingToolCalls();

        List<Msg> history = loop.getHistory();
        Msg injected = history.get(2);
        Content.ToolResultContent trc = (Content.ToolResultContent) injected.contents().get(0);

        // The recovered ToolResult's toolUseId must match the original dangling call ID
        assertEquals(
                danglingId,
                trc.toolUseId(),
                "Recovered ToolResult toolUseId must match the dangling call ID");
    }
}
