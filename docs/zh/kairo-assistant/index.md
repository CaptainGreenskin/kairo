# Kairo Assistant

**Kairo Assistant** 是一个基于 Kairo 框架构建的自托管、多渠道个人 AI 助手。[Kairo Code](/zh/kairo-code/) 专注编码，而 Kairo Assistant 证明了框架可以驱动一个功能完备的个人助手 —— 50+ 工具、8 个消息渠道、自进化、多 Agent 编排。

> **状态：** 早期开发（`0.1.0-SNAPSHOT`），接口可能变更。

## 为什么选 Kairo Assistant？

- **自托管** — 运行在你自己的基础设施上，数据完全自主可控
- **多渠道** — 连接钉钉、飞书、Slack、Telegram、Discord、Mattermost 或任何 Webhook
- **多模型** — 在 Claude、GPT、GLM、DeepSeek、Qwen、Gemini、Kimi、Groq、Ollama 等 10+ 厂商之间自由切换
- **OpenAI 兼容 API** — 提供 `/v1/chat/completions` 和 `/v1/responses` 标准接口，支持流式输出
- **框架 Dogfood** — 真实使用 Kairo 每一个 SPI（sandbox、workspace、plugin、gateway、evolution、expert-team、MCP）

## 核心能力

### 50+ 内置工具

| 分类 | 工具 |
|------|------|
| **个人效率** | Notes、Bookmarks、Contacts、Calendar、Reminders、Todo、UserProfile、Clipboard、Time |
| **文件操作** | Read、Write、Patch、ListDirectory、SearchFiles、FileCheckpoint |
| **执行** | Shell、CodeExecute、Process、Env、Git |
| **Web** | WebFetch、WebSearch、Browser（Playwright）、HttpRequest |
| **AI / 多媒体** | ImageGen（DALL-E）、Vision、Voice（TTS/STT）、VideoGenerate、ComputerUse、Screenshot |
| **多 Agent** | DelegateTask、ExpertTeam、MixtureOfAgents、Kanban、SubagentCoordinator、SendMessage |
| **数据工具** | Calculator、Encode、Json、Text、SystemInfo、Weather、HomeAssistant |
| **高级** | Cron、Goal、McpClient、MemorySearch、SessionSearch、Workflow、SkillCreate、SkillHub、PTC |

### 8 个消息渠道

| 渠道 | 集成方式 |
|------|---------|
| 钉钉 | Webhook + Stream Mode SDK |
| 飞书 / Lark | Webhook + WebSocket SDK |
| Slack | Incoming Webhook |
| Telegram | Webhook |
| Discord | Incoming Webhook + Bot |
| Mattermost | Incoming Webhook |
| 通用 Webhook | 任意 HTTP 端点 |

### 编程式工具调用（PTC）

编写 Python 脚本通过 JSON-RPC 调用工具 —— 将多步工具链折叠为一次调用：

```python
# PTC 脚本：工具以函数形式可用
files = glob("src/**/*.java")
for f in files:
    content = read_file(f)
    if "deprecated" in content.lower():
        print(f"Found deprecated usage in {f}")
```

### 多 Agent 看板

内置看板系统，支持依赖追踪、Swarm 拓扑、熔断器、调度器守护进程，适合复杂的多 Agent 工作流。

### 自进化

Agent 从交互中学习：通过 `skill_create` 创建可复用技能，策展器将技能从 DRAFT → VALIDATED → TRUSTED 逐步提升，经验教训自动回馈到系统提示词。

### 目标与调度

基于 Cron 的周期性目标，支持渠道投递 —— 设置每日简报、周报、定期检查，自动运行并将结果发送到你指定的渠道。

### React 管理控制台

基于 Web 的管理界面（Vite + React + Tailwind）：
- 会话管理和对话历史
- Cron 任务调度和监控
- 插件管理（安装 / 启用 / 禁用）
- 自进化监控和技能策展
- 使用分析和系统提示词编辑

## 管理控制台

React 管理控制台提供对助手的全面可观测与控制 —— 会话、任务、技能、记忆、工具和可观测性。

![控制台演示](/images/kairo-assistant/video/console-demo.gif)

### 仪表盘

![仪表盘](/images/kairo-assistant/console-dashboard.png)

### 聊天

![聊天](/images/kairo-assistant/console-chat.png)

### 会话

![会话](/images/kairo-assistant/console-sessions.png)

### 任务与看板

![任务](/images/kairo-assistant/console-tasks.png)

![看板](/images/kairo-assistant/console-board.png)

使用 cron 表达式调度周期性目标，并在控制台中监控：

![定时调度演示](/images/kairo-assistant/video/demo-cron.gif)

### 技能与自进化

![技能](/images/kairo-assistant/console-skills.png)

![自进化](/images/kairo-assistant/console-evolution.png)

### 记忆

![记忆](/images/kairo-assistant/console-memory.png)

### 工具

![工具](/images/kairo-assistant/console-tools.png)

### 分析与可观测性

![分析](/images/kairo-assistant/console-analytics.png)

![可观测性](/images/kairo-assistant/console-observability.png)

![追踪视图](/images/kairo-assistant/console-trace.png)

### 系统与配置

![系统提示词](/images/kairo-assistant/console-system-prompt.png)

![系统信息](/images/kairo-assistant/console-system.png)

![健康检查](/images/kairo-assistant/console-health.png)

![渠道管理](/images/kairo-assistant/console-channels.png)

![插件管理](/images/kairo-assistant/console-plugins.png)

### 工具调试

![工具 Playground](/images/kairo-assistant/console-tool-playground.png)

![工具历史](/images/kairo-assistant/console-tool-history.png)

### 会话创建与回放

![创建会话](/images/kairo-assistant/console-create.png)

![会话回放](/images/kairo-assistant/console-replay.png)

## 架构

```
kairo-assistant/
├── kairo-assistant-core      — Agent 工厂、50+ 工具、渠道、安全、记忆、目标、国际化
├── kairo-assistant-cli       — 交互式 REPL（60+ 斜杠命令）、单次查询、守护进程、ACP 服务器
└── kairo-assistant-server    — Spring Boot REST/WS/SSE + React 管理控制台 + OpenAI 兼容 API
```

### 三种运行模式

| 模式 | 命令 | 说明 |
|------|------|------|
| **交互式 REPL** | `kairo-assistant` | 60+ 斜杠命令、流式输出、工具审批 |
| **单次查询** | `kairo-assistant -p "query"` | 单条查询，stdout 输出，退出 |
| **服务器** | `kairo-assistant-server` | REST + WebSocket + SSE + 管理控制台 |
| **ACP 服务器** | `kairo-assistant --acp-server` | 标准输入输出 JSON-RPC，编辑器集成 |

### Kairo 框架依赖

Kairo Assistant 引入 11 个 Kairo 模块：

`kairo-core` · `kairo-tools` · `kairo-cron` · `kairo-skill` · `kairo-plugin` · `kairo-mcp` · `kairo-expert-team` · `kairo-multi-agent` · `kairo-evolution` · `kairo-security-pii` · `kairo-observability`

### 沙箱后端

| 后端 | 说明 |
|------|------|
| `LocalSandbox` | 直接进程执行（默认） |
| `DockerSandbox` | 隔离 Docker 容器 |
| `SshSandbox` | SSH 远程执行 |
| `DaytonaSandbox` | Daytona 工作空间集成 |

## Kairo Assistant vs Kairo Code

| 维度 | Kairo Assistant | Kairo Code |
|------|----------------|------------|
| **定位** | 通用个人 AI 助手 | 编码专用 Agent |
| **工具** | 50+（效率、多媒体、智能家居） | 56（文件操作、Git、编码专用） |
| **渠道** | 8 个消息平台 | 终端 / IDE |
| **多 Agent** | 看板、Swarm、调度器 | 团队协调 |
| **目标** | Cron 周期性目标 | 无 |
| **PTC** | Python 脚本调用工具 | 无 |
| **服务器** | REST/WS/SSE + React 管理台 + OpenAI API | Bridge SPI |
| **沙箱** | 4 种后端（本地/Docker/SSH/Daytona） | LocalProcess |
| **人格** | SOUL.md 人格文件 | CLAUDE.md 项目指令 |

## 链接

- [GitHub 仓库](https://github.com/CaptainGreenskin/kairo-assistant)
- [Kairo 框架](/zh/guide/introduction)
- [Kairo Code](/zh/kairo-code/)
