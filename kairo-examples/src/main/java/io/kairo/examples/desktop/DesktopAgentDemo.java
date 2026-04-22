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
package io.kairo.examples.desktop;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryQuery;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelProvider;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.agent.snapshot.CheckpointManager;
import io.kairo.core.agent.snapshot.InMemorySnapshotStore;
import io.kairo.core.memory.ConversationMemory;
import io.kairo.core.memory.JdbcMemoryStore;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.model.AnthropicProvider;
import io.kairo.core.model.openai.OpenAIProvider;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.examples.support.MockModelProvider;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Desktop Agent Demo — demonstrates Kairo v0.5 features:
 *
 * <ul>
 *   <li>Cross-session memory persistence via H2 file-mode JdbcMemoryStore
 *   <li>Checkpoint/rollback via CheckpointManager
 *   <li>Tool context injection (toolDependencies)
 * </ul>
 *
 * <p>Run with mock model (no API key needed):
 *
 * <pre>
 *   mvn exec:java -pl kairo-examples \
 *       -Dexec.mainClass="io.kairo.examples.desktop.DesktopAgentDemo" \
 *       -Dexec.args="--mock"
 * </pre>
 *
 * <p>Run with Anthropic:
 *
 * <pre>
 *   export ANTHROPIC_API_KEY=sk-ant-xxx
 *   mvn exec:java -pl kairo-examples \
 *       -Dexec.mainClass="io.kairo.examples.desktop.DesktopAgentDemo"
 * </pre>
 *
 * <p>Run with Qwen:
 *
 * <pre>
 *   export QWEN_API_KEY=your-key
 *   mvn exec:java -pl kairo-examples \
 *       -Dexec.mainClass="io.kairo.examples.desktop.DesktopAgentDemo" \
 *       -Dexec.args="--qwen"
 * </pre>
 */
public class DesktopAgentDemo {

    /** H2 file-mode database URL for persistent memory across JVM restarts. */
    private static final String H2_URL =
            "jdbc:h2:file:./kairo-desktop-data/memory;AUTO_SERVER=TRUE";

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0] : "";

        System.out.println("=== Kairo Desktop Agent ===");
        System.out.println(
                "Features: persistent memory (H2), checkpoint/rollback, tool context DI");
        System.out.println();

        // 1. Setup H2 file-mode DataSource for cross-session persistence
        DataSource dataSource = createH2DataSource();
        System.out.println("[Storage] H2 file database: ./kairo-desktop-data/memory");

        // 2. Create JdbcMemoryStore (auto-creates schema)
        JdbcMemoryStore memoryStore = new JdbcMemoryStore(dataSource);

        // 3. Create ConversationMemory helper
        ConversationMemory memory = new ConversationMemory(memoryStore, "desktop-agent");

        // 4. Create CheckpointManager with in-memory snapshot store
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        CheckpointManager checkpointManager = new CheckpointManager(snapshotStore);

        // 5. Build agent with tool dependencies (context injection)
        DefaultToolRegistry registry = new DefaultToolRegistry();
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        ModelProvider modelProvider = resolveModelProvider(mode);
        String modelName = resolveModelName(mode);

        Agent agent =
                AgentBuilder.create()
                        .name("desktop-assistant")
                        .model(modelProvider)
                        .modelName(modelName)
                        .tools(registry)
                        .toolExecutor(executor)
                        .toolDependencies(
                                Map.of(
                                        "memoryStore", memoryStore,
                                        "conversationMemory", memory))
                        .systemPrompt(
                                "You are a personal desktop assistant with persistent memory. "
                                        + "You can remember facts across sessions. Be helpful and concise.")
                        .maxIterations(10)
                        .build();

        System.out.println("[Model] " + modelName);
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  /save <name>      — Save a checkpoint");
        System.out.println("  /rollback <name>  — Rollback to a checkpoint");
        System.out.println("  /checkpoints      — List all checkpoints");
        System.out.println("  /remember <k>=<v> — Store a memory (key=value)");
        System.out.println("  /recall <key>     — Recall a memory by key");
        System.out.println("  /memories         — List recent memories");
        System.out.println("  /quit             — Exit");
        System.out.println();

        // 6. Interactive CLI loop
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("You: ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if (input.equals("/quit")) break;

            // --- Checkpoint commands ---
            if (input.startsWith("/save ")) {
                String name = input.substring(6).trim();
                if (name.isEmpty()) {
                    System.out.println("[Error] Usage: /save <name>");
                    continue;
                }
                checkpointManager.savepoint(name, agent).block();
                System.out.println("[Checkpoint saved: " + name + "]");
                continue;
            }

            if (input.startsWith("/rollback ")) {
                String name = input.substring(10).trim();
                if (name.isEmpty()) {
                    System.out.println("[Error] Usage: /rollback <name>");
                    continue;
                }
                AgentSnapshot snapshot = checkpointManager.rollback(name).block();
                if (snapshot == null) {
                    System.out.println("[Error] Checkpoint not found: " + name);
                } else {
                    System.out.println("[Rolled back to: " + name + "]");
                    System.out.println(
                            "[Note] Agent state restored. Conversation history reset to checkpoint.");
                }
                continue;
            }

            if (input.equals("/checkpoints")) {
                List<String> checkpoints =
                        checkpointManager.listCheckpoints().collectList().block();
                if (checkpoints == null || checkpoints.isEmpty()) {
                    System.out.println("[No checkpoints saved]");
                } else {
                    System.out.println("[Checkpoints]");
                    checkpoints.forEach(cp -> System.out.println("  - " + cp));
                }
                continue;
            }

            // --- Memory commands ---
            if (input.startsWith("/remember ")) {
                String kv = input.substring(10).trim();
                int eq = kv.indexOf('=');
                if (eq <= 0) {
                    System.out.println("[Error] Usage: /remember <key>=<value>");
                    continue;
                }
                String key = kv.substring(0, eq).trim();
                String value = kv.substring(eq + 1).trim();
                memory.remember(key, value).block();
                System.out.println("[Remembered: " + key + " -> " + value + "]");
                continue;
            }

            if (input.startsWith("/recall ")) {
                String key = input.substring(8).trim();
                if (key.isEmpty()) {
                    System.out.println("[Error] Usage: /recall <key>");
                    continue;
                }
                String value = memory.recall(key).block();
                if (value == null) {
                    System.out.println("[No memory found for: " + key + "]");
                } else {
                    System.out.println("[Memory] " + key + " -> " + value);
                }
                continue;
            }

            if (input.equals("/memories")) {
                List<MemoryEntry> entries =
                        memoryStore
                                .search(
                                        MemoryQuery.builder()
                                                .agentId("desktop-agent")
                                                .limit(10)
                                                .build())
                                .collectList()
                                .block();
                if (entries == null || entries.isEmpty()) {
                    System.out.println("[No memories stored yet]");
                } else {
                    System.out.println("[Recent Memories]");
                    for (MemoryEntry entry : entries) {
                        System.out.println("  - [" + entry.tags() + "] " + entry.content());
                    }
                }
                continue;
            }

            // --- Normal conversation ---
            try {
                Msg userMsg = MsgBuilder.user(input);
                Msg response = agent.call(userMsg).block();
                if (response != null) {
                    System.out.println("Agent: " + response.text());
                } else {
                    System.out.println("Agent: [no response]");
                }
            } catch (Exception e) {
                System.out.println("[Error] " + e.getMessage());
            }
            System.out.println();
        }

        System.out.println("\n[Session ended. Memories persist in ./kairo-desktop-data/]");
        scanner.close();
    }

    private static DataSource createH2DataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(H2_URL);
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    private static ModelProvider resolveModelProvider(String mode) {
        return switch (mode) {
            case "--mock" -> new MockModelProvider();
            case "--qwen" -> {
                String apiKey = System.getenv("QWEN_API_KEY");
                if (apiKey == null || apiKey.isEmpty()) {
                    System.out.println("ERROR: Set QWEN_API_KEY environment variable");
                    System.exit(1);
                }
                String baseUrl =
                        System.getenv()
                                .getOrDefault(
                                        "QWEN_BASE_URL",
                                        "https://dashscope.aliyuncs.com/compatible-mode/v1");
                yield new OpenAIProvider(apiKey, baseUrl, "/chat/completions");
            }
            case "--glm" -> {
                String apiKey = System.getenv("GLM_API_KEY");
                if (apiKey == null || apiKey.isEmpty()) {
                    System.out.println("ERROR: Set GLM_API_KEY environment variable");
                    System.exit(1);
                }
                String baseUrl =
                        System.getenv()
                                .getOrDefault(
                                        "GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4");
                yield new OpenAIProvider(apiKey, baseUrl, "/chat/completions");
            }
            default -> {
                String apiKey = System.getenv("ANTHROPIC_API_KEY");
                if (apiKey == null || apiKey.isEmpty()) {
                    System.out.println(
                            "[Warning] No ANTHROPIC_API_KEY set. Falling back to mock mode.");
                    yield new MockModelProvider();
                }
                yield new AnthropicProvider(apiKey);
            }
        };
    }

    private static String resolveModelName(String mode) {
        return switch (mode) {
            case "--mock" -> "mock-model";
            case "--qwen" -> System.getenv().getOrDefault("QWEN_MODEL", "qwen-plus");
            case "--glm" -> System.getenv().getOrDefault("GLM_MODEL", "glm-4-plus");
            default -> {
                String apiKey = System.getenv("ANTHROPIC_API_KEY");
                yield (apiKey == null || apiKey.isEmpty())
                        ? "mock-model"
                        : "claude-sonnet-4-20250514";
            }
        };
    }
}
