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
import io.kairo.core.model.OpenAIProvider;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.examples.support.LoggingHook;
import io.kairo.tools.exec.BashTool;
import io.kairo.tools.file.*;

/**
 * Demonstrates the full toolset: Read, Write, Edit, Glob, Grep, and Bash.
 *
 * <p>The agent is asked to create a small project, then search, read, and edit files — exercising
 * all file tools plus bash execution in a single multi-step task.
 *
 * <p>Usage:
 *
 * <pre>
 *   export QWEN_API_KEY=your-key
 *   mvn exec:java -pl kairo-examples -Dexec.mainClass="io.kairo.examples.quickstart.FullToolsetExample"
 * </pre>
 */
public class FullToolsetExample {

    private static final String TASK =
            """
            Complete these steps in /tmp/kairo-toolset-demo/:

            1. Create a directory structure with: src/Main.java and src/Utils.java
            2. Write Main.java that calls Utils.greet("Kairo") and prints the result
            3. Write Utils.java with a static greet(String name) method returning "Hello, " + name + "!"
            4. Use glob to find all .java files in the project
            5. Use grep to search for "greet" across all files
            6. Use edit to change the greeting from "Hello" to "Welcome" in Utils.java
            7. Read Utils.java to verify the edit worked
            8. Compile and run the project with bash

            This exercises: write, glob, grep, edit, read, bash — all 6 file/exec tools.
            """;

    public static void main(String[] args) {
        String apiKey = System.getenv("QWEN_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("ERROR: Set QWEN_API_KEY environment variable");
            return;
        }

        String baseUrl =
                System.getenv()
                        .getOrDefault(
                                "QWEN_BASE_URL",
                                "https://dashscope.aliyuncs.com/compatible-mode/v1");
        String model = System.getenv().getOrDefault("QWEN_MODEL", "qwen-plus");

        System.out.println("=== Kairo Full Toolset Example ===");
        System.out.println("Model: " + model);
        System.out.println("Tools: read, write, edit, glob, grep, bash (6 tools)");
        System.out.println("===============================\n");

        // Register all file tools + bash
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.registerTool(ReadTool.class);
        registry.registerTool(WriteTool.class);
        registry.registerTool(EditTool.class);
        registry.registerTool(GlobTool.class);
        registry.registerTool(GrepTool.class);
        registry.registerTool(BashTool.class);

        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        executor.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.ALLOWED);

        OpenAIProvider provider = new OpenAIProvider(apiKey, baseUrl, "/chat/completions");
        LoggingHook hook = new LoggingHook();

        Agent agent =
                AgentBuilder.create()
                        .name("toolset-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .modelName(model)
                        .systemPrompt(
                                "You are a coding assistant. Use the provided tools to complete tasks. "
                                        + "Do not ask for confirmation, just proceed.")
                        .maxIterations(30)
                        .hook(hook)
                        .contextManager(new DefaultContextManager())
                        .build();

        if (agent instanceof DefaultReActAgent ra) {
            ra.setStreamingEnabled(true);
        }

        System.out.println("📝 Task: " + TASK);
        Msg result = agent.call(MsgBuilder.user(TASK)).block();

        System.out.println("\n========================================");
        System.out.println("  Full Toolset Example complete! " + hook.getIteration() + " iterations");
        System.out.println("========================================");
    }
}
