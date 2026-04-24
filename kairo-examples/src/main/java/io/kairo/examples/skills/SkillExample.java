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
package io.kairo.examples.skills;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.tool.ToolPermission;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.agent.DefaultReActAgent;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.model.openai.OpenAIProvider;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.examples.support.LoggingHook;
import io.kairo.skill.DefaultSkillRegistry;
import io.kairo.skill.SkillLoader;
import io.kairo.tools.exec.BashTool;
import io.kairo.tools.file.ReadTool;
import io.kairo.tools.file.WriteTool;
import io.kairo.tools.skill.SkillListTool;
import io.kairo.tools.skill.SkillLoadTool;
import java.nio.file.Path;

/**
 * Demonstrates the Skill system: loading Markdown-based skills and using them.
 *
 * <p>Skills are defined as Markdown files in the skills/ directory. The agent can list available
 * skills and load them on demand (progressive disclosure).
 *
 * <p>Usage:
 *
 * <pre>
 *   export QWEN_API_KEY=your-key
 *   mvn exec:java -pl kairo-examples -Dexec.mainClass="io.kairo.examples.skills.SkillExample"
 * </pre>
 */
public class SkillExample {

    private static final String TASK =
            """
            You have access to a skill system. Please:

            1. Use skill_list to see what skills are available
            2. Load the "code-review" skill
            3. Now, following the loaded skill's instructions, review this Python code
               and write your review to /tmp/kairo-skill-demo/review.txt:

            ```python
            import os
            def get_user_data(user_id):
                query = "SELECT * FROM users WHERE id = " + user_id
                password = "admin123"
                result = os.popen("curl http://api.example.com/users/" + user_id).read()
                return eval(result)
            ```

            Focus on security issues as the skill instructs.
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

        System.out.println("=== Kairo Skill System Example ===");
        System.out.println("Model: " + model);
        System.out.println("Skills directory: skills/");
        System.out.println("===============================\n");

        // 1. Load skills from the skills/ directory
        DefaultSkillRegistry skillRegistry = new DefaultSkillRegistry();
        SkillLoader skillLoader = new SkillLoader(skillRegistry);

        Path skillDir = Path.of("skills");
        if (!skillDir.toFile().isDirectory()) {
            // Try relative to kairo-examples module
            skillDir = Path.of("../skills");
        }
        System.out.println("Loading skills from: " + skillDir.toAbsolutePath());
        skillLoader.loadFromDirectory(skillDir).collectList().block();
        System.out.println("Loaded " + skillRegistry.list().size() + " skill(s)\n");

        // 2. Register tools including skill tools
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.registerTool(BashTool.class);
        registry.registerTool(WriteTool.class);
        registry.registerTool(ReadTool.class);
        // Skill tools require SkillRegistry in constructor — register definition + instance
        // manually
        SkillListTool skillListTool = new SkillListTool(skillRegistry);
        SkillLoadTool skillLoadTool = new SkillLoadTool(skillRegistry, skillLoader);
        // Scan annotations for tool definitions, then register instances
        var scanner = new io.kairo.core.tool.AnnotationToolScanner();
        registry.register(scanner.scanClass(SkillListTool.class));
        registry.registerInstance("skill_list", skillListTool);
        registry.register(scanner.scanClass(SkillLoadTool.class));
        registry.registerInstance("skill_load", skillLoadTool);

        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);
        executor.setDefaultPermission(ToolSideEffect.SYSTEM_CHANGE, ToolPermission.ALLOWED);

        OpenAIProvider provider = new OpenAIProvider(apiKey, baseUrl, "/chat/completions");
        LoggingHook hook = new LoggingHook();

        Agent agent =
                AgentBuilder.create()
                        .name("skill-agent")
                        .model(provider)
                        .tools(registry)
                        .toolExecutor(executor)
                        .modelName(model)
                        .systemPrompt(
                                "You are a helpful assistant with access to a skill system. "
                                        + "Use skill_list to discover skills and skill_load to activate them. "
                                        + "Follow loaded skill instructions precisely.")
                        .maxIterations(20)
                        .hook(hook)
                        .build();

        if (agent instanceof DefaultReActAgent ra) {
            ra.setStreamingEnabled(true);
        }

        System.out.println("📝 Task: " + TASK);
        Msg result = agent.call(MsgBuilder.user(TASK)).block();

        System.out.println("\n========================================");
        System.out.println("  Skill Example complete! " + hook.getIteration() + " iterations");
        System.out.println("========================================");
    }
}
