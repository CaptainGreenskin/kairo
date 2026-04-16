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
package io.kairo.examples.quickstart;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.tool.ToolPermission;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.agent.DefaultReActAgent;
import io.kairo.core.context.DefaultContextManager;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.model.AnthropicProvider;
import io.kairo.core.model.OpenAIProvider;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.examples.support.LoggingHook;
import io.kairo.examples.support.MockModelProvider;
import io.kairo.tools.exec.BashTool;
import io.kairo.tools.file.ReadTool;
import io.kairo.tools.file.WriteTool;
import java.nio.file.Path;

/**
 * Kairo Example — showcases the complete ReAct reasoning loop.
 *
 * <p>Run with mock model (no API key needed):
 *
 * <pre>
 *   mvn exec:java -pl kairo-examples -Dexec.args="--mock"
 * </pre>
 *
 * <p>Run with GLM (Zhipu) model:
 *
 * <pre>
 *   export GLM_API_KEY=your-api-key
 *   mvn exec:java -pl kairo-examples -Dexec.args="--glm"
 * </pre>
 *
 * <p>Run with Qwen (Alibaba) model:
 *
 * <pre>
 *   export QWEN_API_KEY=your-api-key
 *   mvn exec:java -pl kairo-examples -Dexec.args="--qwen"
 * </pre>
 *
 * <p>Run with real Anthropic API:
 *
 * <pre>
 *   export ANTHROPIC_API_KEY=sk-ant-xxx
 *   mvn exec:java -pl kairo-examples
 * </pre>
 */
public class AgentExample {

    /** Comprehensive E2E task that exercises multiple agent features. */
    private static final String E2E_TASK =
            """
            Please complete the following multi-step task:

            1. First, list the files in /tmp/kairo-e2e-test/ directory (create it if needed).
            2. Create a file /tmp/kairo-e2e-test/hello.py with a Python script that prints "Hello from Kairo!".
            3. Run the Python script using bash and tell me the output.
            4. Read the file you just created to verify its contents.
            5. Create a summary file /tmp/kairo-e2e-test/summary.txt describing what you did.

            This task tests: file read/write, bash execution, and multi-step reasoning.
            """;

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "";

        switch (mode) {
            case "--mock" -> runMockDemo();
            case "--glm" -> {
                String glmApiKey = System.getenv("GLM_API_KEY");
                if (glmApiKey == null || glmApiKey.isEmpty()) {
                    System.out.println("ERROR: Set GLM_API_KEY environment variable");
                    return;
                }
                runGlmDemo(glmApiKey);
            }
            case "--qwen" -> {
                String qwenApiKey = System.getenv("QWEN_API_KEY");
                if (qwenApiKey == null || qwenApiKey.isEmpty()) {
                    System.out.println("ERROR: Set QWEN_API_KEY environment variable");
                    return;
                }
                runQwenDemo(qwenApiKey);
            }
            default -> {
                String apiKey = System.getenv("ANTHROPIC_API_KEY");
                if (apiKey == null || apiKey.isEmpty()) {
                    printUsage();
                    System.out.println("\nNo API key found. Falling back to mock mode...\n");
                    runMockDemo();
                } else {
                    runRealDemo(apiKey);
                }
            }
        }
    }

    private static void runMockDemo() {
        System.out.println("========================================");
        System.out.println("  Kairo — ReAct Example (Mock Mode)");
        System.out.println("========================================");

        String task = "Please create a HelloWorld.java in /tmp/agent-demo, compile and run it.";
        System.out.println("\n\uD83D\uDCDD Task: " + task);

        // 1. Tool registry — register tools by class (annotation scanning + auto-instantiation)
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.registerTool(BashTool.class);
        registry.registerTool(WriteTool.class);
        registry.registerTool(ReadTool.class);

        // 2. Tool executor
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        // 3. Mock model provider
        MockModelProvider mockProvider = new MockModelProvider();

        // 4. Logging hook
        LoggingHook loggingHook = new LoggingHook();

        // 5. Context manager (optional — enables auto-compaction)
        DefaultContextManager contextManager = new DefaultContextManager();

        // 6. Build agent
        Agent agent =
                AgentBuilder.create()
                        .name("demo-agent")
                        .model(mockProvider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .systemPrompt(
                                "You are a helpful coding assistant. Use the provided tools to complete tasks.")
                        .maxIterations(20)
                        .hook(loggingHook)
                        .contextManager(contextManager) // optional: enables auto-compaction
                        .build();

        // 7. Run
        Msg input = MsgBuilder.user(task);
        Msg result = agent.call(input).block();

        // 7. Print summary
        System.out.println("\n========================================");
        System.out.println(
                "  Example complete! ReAct loop: " + loggingHook.getIteration() + " iterations");
        System.out.println("========================================");
    }

    private static void runRealDemo(String apiKey) {
        System.out.println("========================================");
        System.out.println("  Kairo — ReAct Example");
        System.out.println("  Model: Claude Sonnet");
        System.out.println("========================================");

        String task =
                "Please create a HelloWorld.java file in /tmp/agent-demo, compile it with javac, "
                        + "run it with java, and tell me the output.";
        System.out.println("\n\uD83D\uDCDD Task: " + task);

        // 1. Tool registry
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.registerTool(BashTool.class);
        registry.registerTool(WriteTool.class);
        registry.registerTool(ReadTool.class);

        // 2. Tool executor
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        // 3. Real Anthropic provider
        AnthropicProvider provider = new AnthropicProvider(apiKey);

        // 4. Logging hook
        LoggingHook loggingHook = new LoggingHook();

        // 5. Build agent
        Agent agent =
                AgentBuilder.create()
                        .name("coding-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .systemPrompt(
                                "You are a helpful coding assistant. Use the provided tools to complete tasks step by step.")
                        .maxIterations(20)
                        .hook(loggingHook)
                        .build();

        // 6. Run
        Msg input = MsgBuilder.user(task);
        Msg result = agent.call(input).block();

        // 7. Print summary
        System.out.println("\n========================================");
        System.out.println(
                "  Example complete! ReAct loop: " + loggingHook.getIteration() + " iterations");
        System.out.println("========================================");
    }

    private static void runGlmDemo(String apiKey) {
        String baseUrl =
                System.getenv()
                        .getOrDefault("GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4");
        String modelName = System.getenv().getOrDefault("GLM_MODEL", "glm-4-plus");

        printE2EBanner("GLM", modelName, baseUrl);

        // 1. Tool registry
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.registerTool(BashTool.class);
        registry.registerTool(WriteTool.class);
        registry.registerTool(ReadTool.class);

        // 2. Tool executor — set all permissions to ALLOWED for E2E (no console blocking)
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        executor.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.ALLOWED);

        // 3. GLM provider (OpenAI-compatible API)
        OpenAIProvider provider = new OpenAIProvider(apiKey, baseUrl, "/chat/completions");

        // 4. Logging hook
        LoggingHook loggingHook = new LoggingHook();

        // 5. Context manager
        DefaultContextManager contextManager = new DefaultContextManager();

        // 6. Build agent with session persistence
        Agent agent =
                AgentBuilder.create()
                        .name("glm-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .modelName(modelName)
                        .systemPrompt(
                                "You are a helpful coding assistant. Use the provided tools to complete tasks step by step. "
                                        + "Always use the tools available to you. Do not ask for confirmation, just proceed with the task.")
                        .maxIterations(20)
                        .hook(loggingHook)
                        .contextManager(contextManager)
                        .sessionPersistence(Path.of("/tmp/kairo-sessions"))
                        .sessionId("e2e-test-glm")
                        .build();

        // 7. Enable streaming (with fallback if provider doesn't support it)
        if (agent instanceof DefaultReActAgent reactAgent) {
            reactAgent.setStreamingEnabled(true);
            System.out.println("  Streaming: enabled (with non-streaming fallback)");
        }

        System.out.println("\n\uD83D\uDCDD Task: " + E2E_TASK);

        // 8. Run
        Msg input = MsgBuilder.user(E2E_TASK);
        Msg result = agent.call(input).block();

        // 9. Print summary
        System.out.println("\n========================================");
        System.out.println(
                "  Example complete! ReAct loop: " + loggingHook.getIteration() + " iterations");
        System.out.println("========================================");
    }

    private static void runQwenDemo(String apiKey) {
        String baseUrl =
                System.getenv()
                        .getOrDefault(
                                "QWEN_BASE_URL",
                                "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String modelName = System.getenv().getOrDefault("QWEN_MODEL", "qwen-plus");

        printE2EBanner("Qwen", modelName, baseUrl);

        // 1. Tool registry
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.registerTool(BashTool.class);
        registry.registerTool(WriteTool.class);
        registry.registerTool(ReadTool.class);

        // 2. Tool executor — set all permissions to ALLOWED for E2E
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        executor.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.ALLOWED);

        // 3. Qwen provider (OpenAI-compatible API)
        OpenAIProvider provider = new OpenAIProvider(apiKey, baseUrl, "/chat/completions");

        // 4. Logging hook
        LoggingHook loggingHook = new LoggingHook();

        // 5. Context manager
        DefaultContextManager contextManager = new DefaultContextManager();

        // 6. Build agent with session persistence
        Agent agent =
                AgentBuilder.create()
                        .name("qwen-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .modelName(modelName)
                        .systemPrompt(
                                "You are a helpful coding assistant. Use the provided tools to complete tasks step by step. "
                                        + "Always use the tools available to you. Do not ask for confirmation, just proceed with the task.")
                        .maxIterations(20)
                        .hook(loggingHook)
                        .contextManager(contextManager)
                        .sessionPersistence(Path.of("/tmp/kairo-sessions"))
                        .sessionId("e2e-test-qwen")
                        .build();

        // 7. Enable streaming (with fallback if provider doesn't support it)
        if (agent instanceof DefaultReActAgent reactAgent) {
            reactAgent.setStreamingEnabled(true);
            System.out.println("  Streaming: enabled (with non-streaming fallback)");
        }

        System.out.println("\n\uD83D\uDCDD Task: " + E2E_TASK);

        // 8. Run
        Msg input = MsgBuilder.user(E2E_TASK);
        Msg result = agent.call(input).block();

        // 9. Print summary
        System.out.println("\n========================================");
        System.out.println(
                "  Example complete! ReAct loop: " + loggingHook.getIteration() + " iterations");
        System.out.println("========================================");
    }

    private static void printE2EBanner(String mode, String modelName, String baseUrl) {
        System.out.println("=== Kairo E2E Test ===");
        System.out.println("Mode: " + mode);
        System.out.println("Model: " + modelName);
        System.out.println("Base URL: " + baseUrl);
        System.out.println("Session persistence: /tmp/kairo-sessions");
        System.out.println("Tool partition: active (READ_ONLY parallel, WRITE serial)");
        System.out.println("Error recovery: active (3 retry, fallback chain)");
        System.out.println("======================");
    }

    private static void printUsage() {
        System.out.println("========================================");
        System.out.println("  Kairo Example");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  --mock   Mock model (no API key needed)");
        System.out.println("  --glm    GLM (Zhipu AI) — requires GLM_API_KEY");
        System.out.println("  --qwen   Qwen (Alibaba) — requires QWEN_API_KEY");
        System.out.println("  (none)   Anthropic Claude — requires ANTHROPIC_API_KEY");
    }
}
