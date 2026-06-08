# Kairo Code

**Kairo Code** 是完全基于 [Kairo 框架](https://github.com/CaptainGreenskin/kairo) 构建的生产级编程 Agent CLI。它是 Kairo 的核心 dogfooding 应用 -- 用真实的编程工作负载证明 Kairo 的 SPI 驱动架构、响应式运行时和 Agent 基础设施能够在生产环境中正常工作。

源代码：[github.com/CaptainGreenskin/kairo-code](https://github.com/CaptainGreenskin/kairo-code)

## 为什么选择 Kairo Code？

Kairo Code 不是任何现有编程 Agent 的 fork。它是一个 **Java 原生** 编程 Agent，从零开始基于 Kairo 的 Agent OS 抽象构建：

- **框架验证** -- Kairo Code 的每个功能都在使用 Kairo 的 SPI。如果框架缺少某些能力或设计不合理，我们会先修复框架。这个反馈循环确保框架始终保持实用。
- **全链路 Java** -- Agent 运行时、工具执行、上下文管理、模型路由和会话持久化全部是 Java 实现，运行在 Project Reactor 上。核心逻辑不依赖 Python 或 Node。
- **生产级能力继承** -- Kairo Code 直接继承了 Kairo 的熔断器、循环检测、协作取消、6 阶段上下文压缩和权限守卫，无需重新实现。
- **可扩展设计** -- 技能、工具、模型提供者和团队协调器都通过 Kairo 的 SPI 契约实现可插拔。添加新工具或切换模型提供者无需修改 Agent 核心代码。

## 截图展示

### CLI 演示

![CLI Demo](/images/kairo-code/video/cli-demo.gif)

### REPL 启动

![Kairo Code REPL](/images/kairo-code/repl-start.png)

### 工具执行 — Grep 搜索

![工具执行](/images/kairo-code/tool-execution.png)

### 技能系统 — 加载 & Code Review

![技能系统](/images/kairo-code/skill-system.png)

### Plan 模式 — 只读分析

![Plan 模式](/images/kairo-code/plan-mode.png)

### 成本追踪

![成本追踪](/images/kairo-code/cost-tracking.png)

## 桌面应用

Kairo Code 同时提供桌面工作台，集成了聊天 Agent、文件浏览器、源代码管理和自进化工具。

### 主聊天界面

![主聊天界面](/images/kairo-code/06-main-chat-interface.png)

### 文件浏览器侧边栏

![文件浏览器侧边栏](/images/kairo-code/11-explorer-sidebar.png)

### 源代码管理

![源代码管理侧边栏](/images/kairo-code/13-source-control-sidebar.png)

### 自进化

![自进化侧边栏](/images/kairo-code/14-self-evolution-sidebar.png)

Kairo Code 从每次会话中学习 —— 持续积累记忆与知识，并通过 hook 系统优化行为：

![自进化演示](/images/kairo-code/video/demo-evolution.gif)

### 审批模式

通过手动、自动安全、YOLO 三种审批模式控制工具运行方式，并由基于 hook 的安全控制保障：

![工具审批演示](/images/kairo-code/video/demo-approval.gif)

### 浅色主题

![浅色主题界面](/images/kairo-code/19-light-theme-interface.png)

### Agent 工作区

完整的类 IDE 工作区 —— 聊天 Agent、文件浏览器、源代码管理与主题协同工作：

![Agent 工作区演示](/images/kairo-code/video/demo-team.gif)

## 核心特性

| 特性 | 描述 |
|------|------|
| **交互式 REPL** | 流式输出、工具审批提示、富终端渲染 |
| **56 个内置工具** | 文件操作、命令执行、Web、Git、交互、技能、Agent 操作、计划模式、记忆、定时任务、代码智能 |
| **技能系统** | 基于 Markdown 的可扩展能力，运行时加载 |
| **多 Agent 团队** | 派生子 Agent，通过 TeamCoordinator SPI 协调 |
| **IDE 桥接** | 基于 WebSocket 的 Bridge SPI，支持 VS Code、JetBrains 和 Zed 集成 |
| **会话持久化** | FileSessionStorageProvider 支持跨重启的对话延续 |
| **多模型支持** | Claude（原生 Anthropic）、GLM、Qwen、GPT（OpenAI 兼容） |
| **计划模式** | 进入/退出计划模式，进行结构化多步推理 |
| **记忆** | Agent 和团队级别的持久化记忆读写删除 |
| **定时调度** | 创建、管理和触发周期性任务 |

## 工具分类

Kairo Code 内置 56 个工具，分为 11 个类别：

- **文件操作** -- `Read`、`Write`、`Edit`、`Glob`、`Grep`、`Tree`、`Diff`、`BatchRead`、`BatchWrite`、`SearchReplace`、`PatchApply`、`JsonQuery`、`TemplateRender`
- **命令执行** -- `Bash`、`Monitor`、`Mvn`、`Sleep`、`VerifyExecution`
- **Web** -- `WebFetch`、`WebSearch`、`Http`、`OpenApiHttp`
- **Git** -- `Git`、`Github`
- **交互** -- `AskUser`
- **技能** -- `SkillList`、`SkillLoad`、`SkillManage`
- **Agent 操作** -- `AgentSpawn`、`SendMessage`、`TeamCreate`、`TeamDelete`、`TaskCreate`、`TaskGet`、`TaskList`、`TaskUpdate`、`TodoRead`、`TodoWrite`、`Workflow`
- **计划模式** -- `EnterPlanMode`、`ExitPlanMode`、`ListPlans`
- **记忆** -- `MemoryRead`、`MemoryWrite`、`MemoryDelete`、`TeamMemoryRead`、`TeamMemoryWrite`、`TeamMemoryDelete`
- **定时任务** -- `CronCreate`、`CronDelete`、`CronEdit`、`CronList`、`CronPause`、`CronResume`、`CronTrigger`
- **代码智能** -- `Lsp`

## 支持的模型

| 提供者 | 模型 | 集成方式 |
|--------|------|----------|
| Anthropic | Claude Opus、Sonnet、Haiku | 原生 `AnthropicModelProvider` |
| GLM | GLM-4 系列 | OpenAI 兼容适配器 |
| Qwen | Qwen 系列 | OpenAI 兼容适配器 |
| OpenAI | GPT-4o、GPT-4 等 | OpenAI 兼容适配器 |

## 与 Kairo 框架的关系

Kairo Code 作为库消费者依赖 Kairo 框架，依赖关系如下：

```
kairo-code-core
  ├── kairo-core          （ReAct 引擎、上下文压缩、模型提供者）
  ├── kairo-tools         （内置工具套件）
  ├── kairo-mcp           （MCP 协议集成）
  ├── kairo-multi-agent   （团队协调）
  ├── kairo-skill         （技能系统）
  └── kairo-plugin        （插件系统）
```

Kairo Code 不修改或扩展框架内部实现。它通过配置和组装 Kairo 的 SPI 来构建编程 Agent -- 与任何应用使用框架的方式完全一致。
