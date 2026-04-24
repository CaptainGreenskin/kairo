<h1 align="center">Kairo</h1>

<p align="center"><img src="docs/logo.png" alt="Kairo Logo" width="200" /></p>

<h3 align="center">Java Agent 操作系统 — AI Agent 运行时基础设施</h3>

<p align="center">
  <a href="./README.md">English</a>
</p>

<p align="center">
  <a href="https://github.com/CaptainGreenskin/kairo/actions"><img src="https://github.com/CaptainGreenskin/kairo/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <img src="https://img.shields.io/badge/license-Apache--2.0-blue" alt="License" />
  <img src="https://img.shields.io/badge/JDK-17%2B-orange" alt="JDK 17+" />
  <img src="https://img.shields.io/badge/reactive-Project%20Reactor-green" alt="Reactor" />
</p>

---

## 概述

**Kairo**（源自希腊语 *Kairos* — 行动的决定性时刻）是一个 Java Agent 操作系统，为 AI Agent 提供完整的运行时环境。Kairo 不是一个简单的 LLM 封装库，而是将 Agent 运行时的每个组件映射到操作系统概念：

Kairo 不是封装层 — 它是基础设施。正如 Netty 之于网络、Jackson 之于序列化，Kairo 之于 AI Agent。

| OS 概念 | Kairo 映射 | 说明 | 状态 |
|---------|-----------|------|------|
| 内存管理 | Context | 上下文窗口 = 有限内存，需要智能压缩 | Implemented |
| 系统调用 | Tool | 21+ 专用工具，Agent 与外部世界的接口 | Implemented |
| 进程 | Agent | ReAct 循环驱动的独立执行单元 | Implemented |
| 文件系统 | Memory | 持久化知识存储（文件 / 内存 / JDBC） | Implemented |
| 信号处理 | Hook | 10 个钩子点，支持 CONTINUE/MODIFY/SKIP/ABORT/INJECT 决策 | Implemented |
| 可执行文件 | Skill | Markdown 格式的即插即用能力包 | Implemented |
| 作业调度 | Task + Team | 多 Agent 任务编排与团队协作 | Implemented |
| IPC | A2A 协议 | Agent-to-Agent 通信，跨 Agent 调用 | Implemented |
| 中间件 | 中间件管道 | 声明式请求/响应拦截 | Implemented |
| 检查点 | 快照 | Agent 状态序列化与恢复 | Implemented |

基于 Project Reactor 构建，完全响应式、非阻塞执行，开箱即用支持 Claude、GLM、Qwen、GPT 等模型。框架与模型无关 — 切换提供者无需修改 Agent 逻辑。

## 架构

```
kairo-parent
├── kairo-bom                  — BOM 依赖版本管理
├── kairo-api                  — SPI 接口层（零实现依赖）
├── kairo-core                 — 核心运行时（ReAct 引擎、压缩管道、模型提供者）
├── kairo-tools                — 内置工具集（21 个工具）
├── kairo-mcp                  — MCP 协议集成（StreamableHTTP）
├── kairo-multi-agent          — 多 Agent 编排（A2A 协议、Team、TaskBoard）
├── kairo-observability        — OpenTelemetry 集成
├── kairo-spring-boot-starter  — Spring Boot 自动装配
└── kairo-examples             — 示例应用
```

## 核心特性

- **ReAct 引擎** — `DefaultReActAgent` 实现完整的推理-行动循环，支持可配置迭代上限、流式响应和多层错误恢复
- **6 级上下文压缩管道** — 渐进式管道（Snip → Micro → Collapse → Auto → Partial → 熔断器），采用"Facts First"策略尽可能保留原始上下文
- **21 个内置工具** — 文件操作（Read/Write/Edit/Glob/Grep）、执行（Bash/Monitor）、交互（AskUser）、技能（SkillList/SkillLoad）、Agent 操作（Spawn/Message/Task/Team/Plan）
- **读写分区** — READ_ONLY 工具并行执行，WRITE/SYSTEM_CHANGE 工具自动串行化
- **人机协作** — 三态权限模型（ALLOWED/ASK/DENIED），通过 `PermissionGuard` 控制
- **多 Agent 编排** — `TeamCoordinator` SPI（默认 expert-team 编排：plan → generate → evaluate）和进程内 MessageBus
- **A2A 协议** — Agent-to-Agent 通信标准（Google ADK 兼容），进程内发现 + 调用，团队自动注册
- **中间件管道** — 声明式请求/响应拦截，通过 `@MiddlewareOrder` 实现横切关注点（日志、认证、限流）
- **Agent 快照/检查点** — 对话中序列化 Agent 状态，通过 `AgentBuilder.restoreFrom(snapshot)` 从检查点恢复
- **结构化输出** — 调用模型返回类型化 POJO，格式错误时自动自纠正
- **Hook 生命周期** — 10 个钩子点（Pre/Post Reasoning、Acting 等），支持 CONTINUE/MODIFY/SKIP/ABORT/INJECT 决策
- **熔断器** — 模型调用和工具调用的三态熔断器，支持可配置阈值
- **循环检测** — 基于哈希 + 基于频率的双重检测，防止 Agent 无限循环
- **协作取消** — 优雅的 Agent 终止，保留状态
- **MCP 集成** — StreamableHTTP + Elicitation Protocol，连接外部工具服务器
- **技能系统** — Markdown 格式技能定义，`TriggerGuard` 反污染设计
- **计划模式** — 规划与执行分离，规划期间写工具被阻止
- **模型适配** — 深度 Anthropic 集成 + OpenAI 兼容回退（GLM、Qwen、GPT 等）
- **会话持久化** — 基于文件的状态序列化，支持 TTL 自动清理

## 快速开始

**环境要求：** JDK 17+, Maven 3.8+

### 1. 添加依赖

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.captaingreenskin</groupId>
            <artifactId>kairo-bom</artifactId>
            <version>1.0.0-RC1</version>
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

### 2. 编写你的第一个 Agent

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

如果你已经有 Spring 注入的 `Agent` Bean，那么 "Hello World" 只需要 3 行：

```java
@Autowired Agent agent;
Msg reply = agent.call(MsgBuilder.user("你好，Kairo！")).block();
System.out.println(reply.getTextContent());
```

### 3. Spring Boot 集成

添加 starter 依赖，通过 `application.yml` 配置：

```xml
<dependency>
    <groupId>io.github.captaingreenskin</groupId>
    <artifactId>kairo-spring-boot-starter</artifactId>
</dependency>
```

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

几行配置，Agent 即可使用。

## 模型支持

| 提供商 | 模型 | API 类型 | 环境变量 | 状态 |
|--------|------|----------|---------|------|
| **Anthropic** | Claude Sonnet, Claude Opus, Claude Haiku | 原生 Anthropic API | `ANTHROPIC_API_KEY` | Implemented |
| **智谱 AI** | GLM-4-Plus, GLM-4 | OpenAI 兼容 | `GLM_API_KEY` | Implemented |
| **DashScope** | Qwen-Plus, Qwen-Max, Qwen-Turbo | OpenAI 兼容 | `QWEN_API_KEY` | Implemented |
| **OpenAI** | GPT-4o, GPT-4, GPT-3.5 | OpenAI 兼容 | `OPENAI_API_KEY` | Implemented |

```java
// Anthropic（原生 API）
AnthropicProvider claude = new AnthropicProvider(apiKey);

// GLM / Qwen / GPT（OpenAI 兼容）
OpenAIProvider provider = new OpenAIProvider(apiKey, baseUrl, "/chat/completions");
```

## 构建

```bash
# 构建并安装所有模块（运行 Demo 前必须先执行）
mvn clean install

# 仅运行测试（v1.0.0-RC1 基线：2,525 个测试 / 350 个套件）
mvn test
```

### 运行演示

```bash
# Mock 模式（无需 API Key）
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--mock"

# GLM 模式（需要 GLM_API_KEY）
export GLM_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--glm"

# Qwen 模式（需要 QWEN_API_KEY）
export QWEN_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample" \
  -Dexec.args="--qwen"

# Anthropic 模式（需要 ANTHROPIC_API_KEY）
export ANTHROPIC_API_KEY=your-key
mvn exec:java -pl kairo-examples \
  -Dexec.mainClass="io.kairo.examples.quickstart.AgentExample"
```

更多 Demo：

| Demo | 需要 API Key | 测试内容 |
|------|-------------|---------|
| `AgentExample --mock` | 否 | 基础 ReAct 循环（Mock 模型） |
| `AgentExample --glm` | GLM | GLM-4-Plus 的 ReAct 循环 |
| `AgentExample --qwen` | Qwen | Qwen-Plus 的 ReAct 循环 |
| `FullToolsetExample` | Qwen | 全部 6 个工具：read, write, edit, glob, grep, bash |
| `SkillExample` | Qwen | 技能系统：列出、加载和使用 Markdown 技能 |
| `MultiAgentExample` | 否 | TaskBoard DAG 依赖追踪 + MessageBus 发布/订阅 |
| `SessionExample` | 否 | FileMemoryStore + SessionSerializer 序列化往返 |
| Spring Boot Demo | 是 | REST API、流式输出、结构化输出、Hook、MCP |

## Roadmap

| 版本 | 主题 | 状态 |
|------|------|------|
| v0.1–v0.4 | 核心运行时 + SPI + A2A + 中间件 + 快照 | Released |
| v0.5 | 记忆层 — Memory SPI + Embedding + Checkpoint/Rollback | Released |
| v0.6 | 异常 Phase B + 中断/恢复 + Team 模式 | Released |
| v0.7 | Guardrail SPI + 安全可观测 + MCP 默认 DENY_SAFE | Released |
| v0.8 | DurableExecutionStore + ResourceConstraint + 成本感知路由 | Released |
| v0.9.0 | Channel SPI + KairoEventBus + OTel Exporter | Released |
| v0.9.1 | 钉钉 Channel 适配器（首个真实 `Channel` 传输实现） | Released |
| v0.10.2 | 结构性债务清理 — kairo-skill 拆分、ProviderPipeline、MCP 能力 record | Released |
| v1.0.0-RC1 | SPI 稳定化 — 119 `@Stable` / 78 `@Experimental`、japicmp 门禁、77.4% core 覆盖 | Released |
| v1.0.0-RC2 | API 参考文档、中英文完全对齐、可观测 + Channel 示例 | In Progress |
| v1.0.0 GA | 企业安全（PII + 审计 + 合规）、OSS GA 发布仪式 | Planned |

## 贡献

欢迎贡献！请参阅 [CONTRIBUTING.md](./CONTRIBUTING.md) 了解详情。

查看 [`good first issue`](https://github.com/CaptainGreenskin/kairo/labels/good%20first%20issue) 标签快速上手，或 [`help wanted`](https://github.com/CaptainGreenskin/kairo/labels/help%20wanted) 标签挑战更复杂的任务。

## 社区

- [GitHub Discussions](https://github.com/CaptainGreenskin/kairo/discussions) — 提问、想法和讨论
- [GitHub Issues](https://github.com/CaptainGreenskin/kairo/issues) — Bug 报告和功能请求

<!-- TODO: 社区渠道就绪后添加 -->
<!-- - [Discord](https://discord.gg/xxx) -->
<!-- - 微信群 / 钉钉群二维码 -->

## 许可证

Kairo 基于 [Apache License 2.0](./LICENSE) 许可。

```
Copyright 2025-2026 the Kairo authors.
```
