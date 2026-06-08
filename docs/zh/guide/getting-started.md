# 快速开始

## 环境要求

- **JDK 17+**
- **Maven 3.8+**

## 1. 添加依赖

使用 Kairo BOM 进行版本管理：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.captaingreenskin</groupId>
            <artifactId>kairo-bom</artifactId>
            <version>0.8.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.captaingreenskin</groupId>
        <artifactId>kairo-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.captaingreenskin</groupId>
        <artifactId>kairo-tools</artifactId>
    </dependency>
</dependencies>
```

## 2. 编写你的第一个 Agent

```java
// 1. 注册工具
DefaultToolRegistry registry = new DefaultToolRegistry();
registry.registerTool(BashTool.class);
registry.registerTool(WriteTool.class);
registry.registerTool(ReadTool.class);

// 2. 创建工具执行器
DefaultPermissionGuard guard = new DefaultPermissionGuard();
DefaultToolExecutor executor = new DefaultToolExecutor(registry, guard);

// 3. 选择模型提供者
AnthropicProvider provider = new AnthropicProvider(System.getenv("ANTHROPIC_API_KEY"));

// 4. 构建 Agent
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

// 5. 执行
Msg result = agent.call(MsgBuilder.user("创建一个 HelloWorld.java 并编译运行")).block();
```

## 3. Spring Boot 集成

添加 starter 依赖，通过 `application.yml` 配置：

```xml
<dependency>
    <groupId>io.github.captaingreenskin</groupId>
    <artifactId>kairo-spring-boot-starter-core</artifactId>
</dependency>
```

全部 13 个可用 starter：

- `kairo-spring-boot-starter-core` — 核心运行时
- `kairo-spring-boot-starter-mcp` — MCP 协议集成
- `kairo-spring-boot-starter-multi-agent` — 多 Agent 编排
- `kairo-spring-boot-starter-evolution` — 自进化管道
- `kairo-spring-boot-starter-expert-team` — 专家团队编排
- `kairo-spring-boot-starter-event-stream` — 事件流
- `kairo-spring-boot-starter-gateway` — 多 Channel 编排
- `kairo-spring-boot-starter-channel-dingtalk` — 钉钉适配器
- `kairo-spring-boot-starter-observability` — OpenTelemetry 可观测性
- `kairo-spring-boot-starter-plugin` — 插件系统
- `kairo-spring-boot-starter-cron` — 定时任务调度
- `kairo-spring-boot-starter-lsp` — LSP 诊断
- `kairo-spring-boot-starter-acp` — Agent Client Protocol

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

五行配置，Agent 即可使用。
