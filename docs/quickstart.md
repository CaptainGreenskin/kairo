# Getting Started with Kairo

Kairo is a Java Agent OS — a complete runtime for AI agents with smart context management, tool execution, and multi-agent orchestration.

## Prerequisites

- **JDK 17+** (project compiles with Java 17)
- **Maven 3.8+**

## Add Dependency

```xml
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-tools</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Your First Agent (5 minutes)

```java
import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.core.agent.AgentBuilder;
import io.kairo.core.message.MsgBuilder;
import io.kairo.core.model.OpenAIProvider;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.tools.exec.BashTool;
import io.kairo.tools.file.ReadTool;
import io.kairo.tools.file.WriteTool;

public class MyFirstAgent {
    public static void main(String[] args) {
        // 1. Register tools
        DefaultToolRegistry registry = new DefaultToolRegistry();
        registry.registerTool(BashTool.class);
        registry.registerTool(WriteTool.class);
        registry.registerTool(ReadTool.class);

        // 2. Create tool executor
        DefaultPermissionGuard guard = new DefaultPermissionGuard();
        DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

        // 3. Create a model provider (OpenAI-compatible)
        String apiKey = System.getenv("GLM_API_KEY");
        OpenAIProvider provider = new OpenAIProvider(
            apiKey,
            "https://open.bigmodel.cn/api/paas/v4",
            "/chat/completions"
        );

        // 4. Build the agent
        Agent agent = AgentBuilder.create()
            .name("my-agent")
            .model(provider)
            .modelName("glm-4-plus")
            .tools(registry)
            .toolExecutor(executor)
            .systemPrompt("You are a helpful coding assistant.")
            .maxIterations(10)
            .build();

        // 5. Run it
        Msg input = MsgBuilder.user("Create a hello world program in /tmp/hello.py and run it.");
        Msg result = agent.call(input).block();
    }
}
```

## Adding Tools

Kairo ships with 20+ built-in tools across 6 categories. Register them by class:

```java
DefaultToolRegistry registry = new DefaultToolRegistry();

// File tools
registry.registerTool(ReadTool.class);    // Read file contents
registry.registerTool(WriteTool.class);   // Write files
registry.registerTool(EditTool.class);    // Search-and-replace editing
registry.registerTool(GlobTool.class);    // File pattern matching
registry.registerTool(GrepTool.class);    // Regex content search

// Execution tools
registry.registerTool(BashTool.class);    // Shell command execution

// Interaction tools
registry.registerTool(AskUserTool.class); // Request user input
```

Tools are auto-partitioned: `READ_ONLY` tools run in parallel, `WRITE` and `SYSTEM_CHANGE` tools run serially.

## Model Configuration

Kairo supports multiple model providers through a unified interface:

```java
// GLM (Zhipu AI)
export GLM_API_KEY=your-key
OpenAIProvider glm = new OpenAIProvider(apiKey, "https://open.bigmodel.cn/api/paas/v4", "/chat/completions");
// modelName: "glm-4-plus"

// Qwen (Alibaba)
export QWEN_API_KEY=your-key
OpenAIProvider qwen = new OpenAIProvider(apiKey, "https://dashscope.aliyuncs.com/compatible-mode/v1", "/chat/completions");
// modelName: "qwen-plus"

// Claude (Anthropic — native API)
AnthropicProvider claude = new AnthropicProvider(System.getenv("ANTHROPIC_API_KEY"));
// modelName: "claude-sonnet-4-20250514"

// GPT-4o (OpenAI)
OpenAIProvider gpt = new OpenAIProvider(apiKey, "https://api.openai.com/v1", "/chat/completions");
// modelName: "gpt-4o"
```

## Enabling Streaming

Enable streaming for real-time token output (supported by GLM, Qwen, Claude, GPT):

```java
Agent agent = AgentBuilder.create()
    .name("streaming-agent")
    .model(provider)
    // ... other config ...
    .build();

// Enable streaming on the agent instance
if (agent instanceof DefaultReActAgent reactAgent) {
    reactAgent.setStreamingEnabled(true);
}
```

Streaming automatically falls back to non-streaming if the provider doesn't support it.

## Session Persistence

Persist agent sessions to disk so state survives restarts:

```java
Agent agent = AgentBuilder.create()
    .name("persistent-agent")
    .model(provider)
    .tools(registry)
    .toolExecutor(executor)
    .systemPrompt("You are a helpful assistant.")
    .maxIterations(20)
    .sessionPersistence(Path.of("/tmp/kairo-sessions"))
    .sessionId("my-session-001")
    .build();
```

Sessions are stored as atomic files using `FileMemoryStore`. The `SessionManager` handles TTL-based cleanup automatically.

## Plan Mode

Agents can enter plan mode to reason about complex tasks before executing:

- **Enter plan mode**: The agent uses `EnterPlanModeTool` to switch into planning. Write tools are blocked — the agent can only read and think.
- **Exit plan mode**: The agent uses `ExitPlanModeTool` to resume normal execution.
- Plans are persisted to `.kairo/plans/` for review and recovery.

## Error Recovery

Kairo handles errors automatically with multi-layer recovery:

- **`prompt_too_long`** — Triggers context compaction pipeline (6 progressive stages)
- **`rate_limited`** — Automatic retry with exponential backoff
- **`server_error`** — Retry up to 3 times, then fallback to next model
- **`max_output_tokens`** — Automatically continues the response

Configure fallback models for resilience:

```java
ModelConfig config = ModelConfig.builder()
    .model("glm-4-plus")
    .maxTokens(4096)
    .fallbackModels(List.of("qwen-plus", "gpt-4o"))
    .build();
```

## Running the Demo

```bash
# Clone and build (install is required for cross-module demos)
git clone https://github.com/CaptainGreenskin/kairo.git
cd kairo
mvn clean install

# Mock mode (no API key needed)
mvn exec:java -pl kairo-examples -Dexec.mainClass="io.kairo.demo.AgentDemo" -Dexec.args="--mock"

# Qwen mode
export QWEN_API_KEY=your-key
mvn exec:java -pl kairo-examples -Dexec.mainClass="io.kairo.demo.AgentDemo" -Dexec.args="--qwen"

# Full toolset demo (requires Qwen API key)
mvn exec:java -pl kairo-examples -Dexec.mainClass="io.kairo.demo.FullToolsetDemo"

# Multi-agent demo (no API key needed)
mvn exec:java -pl kairo-examples -Dexec.mainClass="io.kairo.demo.MultiAgentDemo"
```

## Next Steps

- Browse [kairo-examples](../kairo-examples/src/main/java/io/kairo/demo/) for complete runnable examples
- Explore the [API module](../kairo-api/) for all extension points and SPI interfaces
- Check [kairo-tools](../kairo-tools/) for the full built-in tool catalog
- See [kairo-multi-agent](../kairo-multi-agent/) for Task, Team, and MessageBus orchestration
