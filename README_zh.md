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

| OS 概念 | Kairo 映射 | 说明 |
|---------|-----------|------|
| 内存管理 | Context | 上下文窗口 = 有限内存，需要智能压缩 |
| 系统调用 | Tool | 21+ 专用工具，Agent 与外部世界的接口 |
| 进程 | Agent | ReAct 循环驱动的独立执行单元 |
| 文件系统 | Memory | 持久化知识存储（文件 / 内存） |
| 信号处理 | Hook | 生命周期事件链（Pre/Post Reasoning、Acting） |
| 可执行文件 | Skill | Markdown 格式的即插即用能力包 |
| 作业调度 | Task + Team | 多 Agent 任务编排与团队协作 |

基于 Project Reactor 构建，完全响应式、非阻塞执行，开箱即用支持 Claude、GLM、Qwen、GPT 等模型。

## 架构

```
kairo-parent
├── kairo-bom                  — BOM 依赖版本管理
├── kairo-api                  — SPI 接口层（零实现依赖）
├── kairo-core                 — 核心运行时（ReAct 引擎、压缩管道、模型提供者）
├── kairo-tools                — 内置工具集（21 个工具）
├── kairo-mcp                  — MCP 协议集成 (@Experimental)
├── kairo-multi-agent          — 多 Agent 编排（TaskBoard、TeamScheduler）
├── kairo-spring-boot-starter  — Spring Boot 自动装配
└── kairo-examples             — 示例应用
```

## 核心特性

- **ReAct 引擎** — `DefaultReActAgent` 实现完整的推理-行动循环，支持可配置迭代上限、流式响应和多层错误恢复
- **6 级上下文压缩管道** — 渐进式管道（Snip → Micro → Collapse → Auto → Partial → 熔断器），采用"Facts First"策略尽可能保留原始上下文
- **21 个内置工具** — 文件操作（Read/Write/Edit/Glob/Grep）、执行（Bash/Monitor）、交互（AskUser）、技能（SkillList/SkillLoad）、Agent 操作（Spawn/Message/Task/Team/Plan）
- **读写分区** — READ_ONLY 工具并行执行，WRITE/SYSTEM_CHANGE 工具自动串行化
- **人机协作** — 三态权限模型（ALLOWED/ASK/DENIED），通过 `PermissionGuard` 控制
- **多 Agent 编排** — TaskBoard、PlanBuilder、TeamScheduler 和进程内 MessageBus
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

### 3. Spring Boot 集成

添加 starter 依赖，通过 `application.yml` 配置：

```xml
<dependency>
    <groupId>io.github.captainreenskin</groupId>
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

五行配置，Agent 即可使用。

## 模型支持

| 提供商 | 模型 | API 类型 | 环境变量 |
|--------|------|----------|---------|
| **Anthropic** | Claude Sonnet, Claude Opus, Claude Haiku | 原生 Anthropic API | `ANTHROPIC_API_KEY` |
| **智谱 AI** | GLM-4-Plus, GLM-4 | OpenAI 兼容 | `GLM_API_KEY` |
| **DashScope** | Qwen-Plus, Qwen-Max, Qwen-Turbo | OpenAI 兼容 | `QWEN_API_KEY` |
| **OpenAI** | GPT-4o, GPT-4, GPT-3.5 | OpenAI 兼容 | `OPENAI_API_KEY` |

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

# 仅运行测试
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

## 贡献

欢迎贡献！请参阅 [CONTRIBUTING.md](./CONTRIBUTING.md) 了解详情。

查看 [`good first issue`](https://github.com/CaptainGreenskin/kairo/labels/good%20first%20issue) 标签快速上手，或 [`help wanted`](https://github.com/CaptainGreenskin/kairo/labels/help%20wanted) 标签挑战更复杂的任务。

## 社区

- [GitHub Discussions](https://github.com/CaptainGreenskin/kairo/discussions) — 提问、想法和讨论
- [GitHub Issues](https://github.com/CaptainGreenskin/kairo/issues) — Bug 报告和功能请求

<!-- TODO: 社区渠道就绪后添加 -->
<!-- - [Discord](https://discord.gg/xxx) -->
<!-- - 微信群 / 钉钉群二维码 -->

## 致谢

Kairo 受到以下开源项目的启发：

- [AgentScope Java](https://github.com/agentscope-ai/agentscope-java)（Apache 2.0，阿里巴巴）— Kairo 的模块化 Maven 结构和 Hook 生命周期概念受到 AgentScope 的 Agent 式编程架构方法的启发。（无运行时依赖 AgentScope Java。）
- [Claude Code](https://docs.anthropic.com/en/docs/claude-code)（Anthropic）— Kairo 的三态权限模型（allow/ask/deny）、上下文压缩策略、读写工具分区和计划模式隔离受到 Anthropic Claude Code 设计模式的启发。

Kairo 在此基础上做出了原创贡献，包括 OS 隐喻架构、多级上下文压缩管道、带反污染设计的技能系统，以及深度 Anthropic Prompt Caching 集成。

## 许可证

Kairo 基于 [Apache License 2.0](./LICENSE) 许可。

```
Copyright 2025-2026 the Kairo authors.
```
