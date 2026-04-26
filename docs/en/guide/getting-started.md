# Getting Started

## Prerequisites

- **JDK 17+**
- **Maven 3.8+**

## 1. Add Dependency

Use the Kairo BOM for version management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.captainreenskin</groupId>
            <artifactId>kairo-bom</artifactId>
            <version>0.5.0-SNAPSHOT</version>
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

## 2. Write Your First Agent

```java
// 1. Register tools
DefaultToolRegistry registry = new DefaultToolRegistry();
registry.registerTool(BashTool.class);
registry.registerTool(WriteTool.class);
registry.registerTool(ReadTool.class);

// 2. Create tool executor with permission guard
DefaultPermissionGuard guard = new DefaultPermissionGuard();
DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

// 3. Choose a model provider
AnthropicProvider provider = new AnthropicProvider(System.getenv("ANTHROPIC_API_KEY"));

// 4. Build the agent
Agent agent = AgentBuilder.create()
    .name("coding-agent")
    .model(provider)
    .modelName("claude-sonnet-4-20250514")
    .tools(registry)
    .toolExecutor(executor)
    .systemPrompt("You are a helpful coding assistant.")
    .maxIterations(20)
    .streaming(true)
    .build();

// 5. Run
Msg result = agent.call(MsgBuilder.user("Create a HelloWorld.java, compile and run it.")).block();
```

## 3. Spring Boot Integration

Add the starter dependency and configure via `application.yml`:

```xml
<dependency>
    <groupId>io.github.captaingreenskin</groupId>
    <artifactId>kairo-spring-boot-starter-core</artifactId>
</dependency>
```

Add per-feature starters as needed: `kairo-spring-boot-starter-mcp`, `-multi-agent`, `-evolution`, `-expert-team`, `-event-stream`, `-channel`, `-channel-dingtalk`, `-observability`.

```yaml
kairo:
  model:
    provider: anthropic
    api-key: ${ANTHROPIC_API_KEY}
  tool:
    enable-file-tools: true
    enable-exec-tools: true
```

```java
@Autowired
Agent agent;

@PostMapping("/chat")
public String chat(@RequestBody String message) {
    return agent.call(MsgBuilder.user(message)).block().getTextContent();
}
```

That's it — five lines of YAML and the agent is ready.
