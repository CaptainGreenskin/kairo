# Getting Started with Kairo

Kairo is a Java Agent OS — a complete runtime for AI agents with smart context management, tool execution, and multi-agent orchestration.

## Prerequisites

- **JDK 17+** (project compiles with Java 17)
- **Maven 3.8+**

## Add Dependency

Use the BOM to manage all Kairo module versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.captainreenskin</groupId>
            <artifactId>kairo-bom</artifactId>
            <version>0.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.captainreenskin</groupId>
        <artifactId>kairo-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.captainreenskin</groupId>
        <artifactId>kairo-tools</artifactId>
    </dependency>
</dependencies>
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
            .streaming(true)
            .build();

        // 5. Run it
        Msg input = MsgBuilder.user("Create a hello world program in /tmp/hello.py and run it.");
        Msg result = agent.call(input).block();
    }
}
```

## Spring Boot Integration

Add the starter and configure via YAML — no boilerplate required:

```xml
<dependency>
    <groupId>io.github.captainreenskin</groupId>
    <artifactId>kairo-spring-boot-starter</artifactId>
</dependency>
<!-- Tools are optional — only add what you need -->
<dependency>
    <groupId>io.github.captainreenskin</groupId>
    <artifactId>kairo-tools</artifactId>
</dependency>
```

```yaml
# application.yml
kairo:
  model:
    provider: anthropic        # or: openai
    api-key: ${ANTHROPIC_API_KEY}
    model-name: claude-sonnet-4-20250514
  tool:
    enable-file-tools: true
    enable-exec-tools: true
  agent:
    name: my-agent
    max-iterations: 20
```

```java
@RestController
public class ChatController {

    private final Agent agent;

    public ChatController(Agent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String message) {
        return agent.call(MsgBuilder.user(message)).block().getTextContent();
    }
}
```

See the [Spring Boot demo](../kairo-examples/spring-boot-demo/) for complete examples including streaming, structured output, hooks, and custom tools.

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

You can also register all tools in a package at once:

```java
registry.scan("io.kairo.tools.file");   // All file tools
registry.scan("io.kairo.tools.exec");   // All execution tools
```

## Model Configuration

Kairo supports multiple model providers through a unified interface:

```java
// Anthropic (native API)
AnthropicProvider claude = new AnthropicProvider(System.getenv("ANTHROPIC_API_KEY"));
// modelName: "claude-sonnet-4-20250514"

// GLM (Zhipu AI — OpenAI-compatible)
OpenAIProvider glm = new OpenAIProvider(
    System.getenv("GLM_API_KEY"),
    "https://open.bigmodel.cn/api/paas/v4",
    "/chat/completions");
// modelName: "glm-4-plus"

// Qwen (Alibaba — OpenAI-compatible)
OpenAIProvider qwen = new OpenAIProvider(
    System.getenv("QWEN_API_KEY"),
    "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "/chat/completions");
// modelName: "qwen-plus"

// GPT-4o (OpenAI)
OpenAIProvider gpt = new OpenAIProvider(
    System.getenv("OPENAI_API_KEY"),
    "https://api.openai.com/v1",
    "/chat/completions");
// modelName: "gpt-4o"
```

## Enabling Streaming

Enable streaming for real-time token output:

```java
Agent agent = AgentBuilder.create()
    .name("streaming-agent")
    .model(provider)
    .modelName("gpt-4o")
    .streaming(true)              // <-- enables streaming
    // ... other config ...
    .build();
```

Streaming automatically falls back to non-streaming if the provider doesn't support it.

## Session Persistence

Persist agent sessions to disk so state survives restarts:

```java
Agent agent = AgentBuilder.create()
    .name("persistent-agent")
    .model(provider)
    .modelName("glm-4-plus")
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

## Running the Demo

```bash
# Clone and build (install is required for cross-module demos)
git clone https://github.com/CaptainGreenskin/kairo.git
cd kairo
mvn clean install

# Mock mode (no API key needed)
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--mock"

# GLM mode (requires GLM_API_KEY)
export GLM_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--glm"

# Qwen mode (requires QWEN_API_KEY)
export QWEN_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--qwen"

# Anthropic mode (requires ANTHROPIC_API_KEY)
export ANTHROPIC_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample"

# Full toolset demo (requires QWEN_API_KEY)
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.FullToolsetExample"

# Multi-agent demo (no API key needed)
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.multiagent.MultiAgentExample"

# Session demo (no API key needed)
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.session.SessionExample"

# Skill demo (requires QWEN_API_KEY)
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.skills.SkillExample"

# Spring Boot demo (requires ANTHROPIC_API_KEY or OPENAI_API_KEY)
cd kairo-examples/spring-boot-demo
mvn spring-boot:run
```

## Next Steps

- Browse [kairo-examples](../kairo-examples/src/main/java/io/kairo/examples/) for complete runnable examples
- Explore the [API module](../kairo-api/) for all extension points and SPI interfaces
- Check [kairo-tools](../kairo-tools/) for the full built-in tool catalog
- See [kairo-multi-agent](../kairo-multi-agent/) for Task, Team, and MessageBus orchestration
