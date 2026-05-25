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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReasoningPhase#buildSyntheticStreamingResponse} — the function that turns
 * the streaming detector's view of a turn (text accumulator + ordered tool calls + eager executor
 * results) into the {@link ModelResponse} delivered to PostReasoning hooks / checkpoint / UI
 * bridge.
 *
 * <p>The synthesis is correctness-critical for OpenAI-compatible providers like MiniMax M2 and
 * Zhipu GLM which emit {@code finish_reason} in the same SSE chunk as {@code tool_calls}: the
 * detector sees the tool, but the executor can race the {@code collectList()} and return no results
 * — losing the {@code tool_use} block would silently drop the model's work.
 */
class ReasoningPhaseStreamingSynthesisTest {

    @Test
    void detectorSawToolButExecutorReturnedNothing_stillEmitsToolUseContent() {
        // MiniMax / Zhipu race: detector emits tool, finish_reason completes the stream,
        // executor hasn't published results yet → toolResults is empty. The synthesized
        // response must still carry the ToolUseContent so the agent loop picks up the call.
        var nameMap = new LinkedHashMap<String, String>();
        nameMap.put("call-1", "Read");
        var argsMap = new LinkedHashMap<String, Map<String, Object>>();
        argsMap.put("call-1", Map.of("path", "/tmp/foo.txt"));

        ModelResponse response =
                ReasoningPhase.buildSyntheticStreamingResponse(
                        List.of(), nameMap, argsMap, "", "minimax-m2", 100);

        assertThat(response.stopReason()).isEqualTo(ModelResponse.StopReason.TOOL_USE);
        assertThat(response.contents()).hasSize(1);
        var tu = (Content.ToolUseContent) response.contents().get(0);
        assertThat(tu.toolId()).isEqualTo("call-1");
        assertThat(tu.toolName()).isEqualTo("Read");
        assertThat(tu.input()).containsEntry("path", "/tmp/foo.txt");
        assertThat(tu.input()).doesNotContainKey("_streaming_result");
    }

    @Test
    void accumulatedTextPrependsToolUseContent_bothSurvive() {
        // When the model streams thinking text and then a tool call, the text must NOT be
        // dropped on the floor — checkpoint and UI bridge both rely on it.
        var nameMap = new LinkedHashMap<String, String>();
        nameMap.put("call-1", "Bash");
        var argsMap = new LinkedHashMap<String, Map<String, Object>>();
        argsMap.put("call-1", Map.of("command", "ls"));

        ModelResponse response =
                ReasoningPhase.buildSyntheticStreamingResponse(
                        List.of(ToolResult.success("call-1", "file1\nfile2", Map.of())),
                        nameMap,
                        argsMap,
                        "Let me list the directory.",
                        "minimax-m2",
                        100);

        assertThat(response.contents()).hasSize(2);
        assertThat(((Content.TextContent) response.contents().get(0)).text())
                .isEqualTo("Let me list the directory.");
        var tu = (Content.ToolUseContent) response.contents().get(1);
        assertThat(tu.toolName()).isEqualTo("Bash");
        assertThat(tu.input()).containsEntry("command", "ls");
        assertThat(tu.input()).containsEntry("_streaming_result", "file1\nfile2");
    }

    @Test
    void multipleToolsPreserveDetectorEmissionOrder() {
        // Order matters: the agent loop dispatches tools in the order they appear in the
        // response. If the synthesis re-orders (e.g. via the executor's completion order)
        // the model's intent is corrupted.
        var nameMap = new LinkedHashMap<String, String>();
        nameMap.put("call-a", "Read");
        nameMap.put("call-b", "Bash");
        nameMap.put("call-c", "Write");
        var argsMap = new LinkedHashMap<String, Map<String, Object>>();
        argsMap.put("call-a", Map.of("path", "/a"));
        argsMap.put("call-b", Map.of("command", "true"));
        argsMap.put("call-c", Map.of("path", "/c", "content", "hi"));

        // Executor finishes results in reverse order — should NOT influence response order.
        ModelResponse response =
                ReasoningPhase.buildSyntheticStreamingResponse(
                        List.of(
                                ToolResult.success("call-c", "written", Map.of()),
                                ToolResult.success("call-a", "contents-of-a", Map.of())),
                        nameMap,
                        argsMap,
                        "",
                        "minimax-m2",
                        100);

        assertThat(response.contents()).hasSize(3);
        assertThat(((Content.ToolUseContent) response.contents().get(0)).toolId())
                .isEqualTo("call-a");
        assertThat(((Content.ToolUseContent) response.contents().get(1)).toolId())
                .isEqualTo("call-b");
        assertThat(((Content.ToolUseContent) response.contents().get(2)).toolId())
                .isEqualTo("call-c");
        // call-b had no executor result → no marker; call-a + call-c have markers.
        assertThat(((Content.ToolUseContent) response.contents().get(0)).input())
                .containsEntry("_streaming_result", "contents-of-a");
        assertThat(((Content.ToolUseContent) response.contents().get(1)).input())
                .doesNotContainKey("_streaming_result");
        assertThat(((Content.ToolUseContent) response.contents().get(2)).input())
                .containsEntry("_streaming_result", "written");
    }

    @Test
    void noToolsAndNoText_returnsEndTurnWithEmptyContents() {
        // Defensive: caller is supposed to short-circuit to fallback before calling us, but
        // if it doesn't we must still return a well-formed END_TURN response (not crash).
        ModelResponse response =
                ReasoningPhase.buildSyntheticStreamingResponse(
                        List.of(),
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>(),
                        "",
                        "minimax-m2",
                        50);

        assertThat(response.stopReason()).isEqualTo(ModelResponse.StopReason.END_TURN);
        assertThat(response.contents()).isEmpty();
        assertThat(response.usage().inputTokens()).isEqualTo(50);
    }

    @Test
    void textOnlyNoTools_returnsEndTurnWithSingleTextContent() {
        // Mostly handled by the caller's text-only branch, but make the function's behavior
        // explicit: tools-empty + text-non-empty == END_TURN + one TextContent.
        ModelResponse response =
                ReasoningPhase.buildSyntheticStreamingResponse(
                        List.of(),
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>(),
                        "all done.",
                        "minimax-m2",
                        50);

        assertThat(response.stopReason()).isEqualTo(ModelResponse.StopReason.END_TURN);
        assertThat(response.contents()).hasSize(1);
        assertThat(((Content.TextContent) response.contents().get(0)).text())
                .isEqualTo("all done.");
    }
}
