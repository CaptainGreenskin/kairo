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
package io.kairo.demo;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Mock model provider that simulates a ReAct agent loop without calling a real LLM.
 *
 * <p>Generates pre-scripted tool call sequences to demonstrate the full Reasoning + Acting cycle:
 * create directory, write file, compile & run.
 */
public class MockModelProvider implements ModelProvider {

    private int callCount = 0;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
        callCount++;
        return Mono.defer(
                () -> {
                    // Simulate a small delay
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }

                    return switch (callCount) {
                        case 1 ->
                                simulateToolCall(
                                        "bash",
                                        Map.of("command", "mkdir -p /tmp/agent-demo"),
                                        "I need to create the project directory first.");
                        case 2 ->
                                simulateToolCall(
                                        "write",
                                        Map.of(
                                                "path",
                                                "/tmp/agent-demo/HelloWorld.java",
                                                "content",
                                                "public class HelloWorld {\n    public static void main(String[] args) {\n        System.out.println(\"Hello from Kairo!\");\n    }\n}"),
                                        "Now I'll write the HelloWorld.java file.");
                        case 3 ->
                                simulateToolCall(
                                        "bash",
                                        Map.of(
                                                "command",
                                                "cd /tmp/agent-demo && javac HelloWorld.java && java HelloWorld"),
                                        "Let me compile and run the Java file.");
                        default ->
                                simulateTextResponse(
                                        "\u2705 Task complete! Here's what I did:\n"
                                                + "1. Created the /tmp/agent-demo directory\n"
                                                + "2. Wrote HelloWorld.java with a simple main method\n"
                                                + "3. Compiled and ran it successfully — output: Hello from Kairo!");
                    };
                });
    }

    @Override
    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
        return call(messages, config).flux();
    }

    /** Reset the call counter so the provider can be reused. */
    public void reset() {
        callCount = 0;
    }

    public int getCallCount() {
        return callCount;
    }

    // ---- Helpers ----

    private Mono<ModelResponse> simulateToolCall(
            String toolName, Map<String, Object> input, String thinkingText) {
        List<Content> contents = new ArrayList<>();
        // Add thinking content to show the reasoning step
        contents.add(new Content.ThinkingContent(thinkingText, 0));
        // Add tool use content
        String toolId = "toolu_" + UUID.randomUUID().toString().substring(0, 12);
        contents.add(new Content.ToolUseContent(toolId, toolName, input));

        ModelResponse response =
                new ModelResponse(
                        "msg_mock_" + callCount,
                        contents,
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.TOOL_USE,
                        "mock-model");
        return Mono.just(response);
    }

    private Mono<ModelResponse> simulateTextResponse(String text) {
        ModelResponse response =
                new ModelResponse(
                        "msg_mock_" + callCount,
                        List.of(new Content.TextContent(text)),
                        new ModelResponse.Usage(100, 80, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "mock-model");
        return Mono.just(response);
    }
}
