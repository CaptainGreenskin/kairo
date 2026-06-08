# 架构

## 模块结构

Kairo Code 由四个模块组成：

```
kairo-code/
├── kairo-code-cli         — REPL 界面、终端渲染、命令分发
├── kairo-code-core        — Agent 配置、工具装配、技能加载、会话管理
├── kairo-code-server      — Bridge SPI 服务器，用于 IDE 集成（WebSocket）
└── kairo-code-examples    — 示例配置和用法演示
```

### kairo-code-cli

面向用户的终端层。职责：

- **REPL 循环** -- 读取用户输入，分发到 Agent，渲染流式输出
- **终端渲染** -- Markdown 格式化、语法高亮、工具审批提示
- **命令分发** -- 解析斜杠命令（`/plan`、`/skills`、`/clear`、`/exit`）并路由

### kairo-code-core

Agent 组装层。该模块将 Kairo 框架的 SPI 装配为一个可运行的编程 Agent：

- **Agent 配置** -- 使用合适的模型提供者、工具、技能和 Hook 构建 `DefaultReActAgent`
- **工具装配** -- 将全部 56 个内置工具注册到 `DefaultToolRegistry`，配置权限守卫
- **技能加载** -- 从工作空间和用户级目录加载基于 Markdown 的技能
- **会话管理** -- 配置 `FileSessionStorageProvider` 实现对话持久化

### kairo-code-server

WebSocket 服务器，通过 Bridge SPI 暴露编程 Agent，实现 IDE 集成：

- **VS Code** -- 扩展通过 WebSocket 连接
- **JetBrains** -- 插件通过 WebSocket 连接
- **Zed** -- 通过 Agent Client Protocol (ACP) 使用 stdio 通信

### kairo-code-examples

示例配置，演示不同场景：模型选择、自定义工具注册、技能编写、团队协调模式。

## 框架依赖

Kairo Code 作为库消费者使用 Kairo 框架。依赖关系图：

```
kairo-code-cli
└── kairo-code-core
    ├── kairo-core              — ReAct 引擎、上下文压缩、模型提供者
    ├── kairo-tools             — 17+ 框架级工具（在 kairo-code 中扩展到 56 个）
    ├── kairo-mcp               — MCP 协议，连接外部工具服务器
    ├── kairo-multi-agent       — A2A 协议、TeamCoordinator、MessageBus
    ├── kairo-skill             — 技能注册与加载
    ├── kairo-plugin            — 插件系统（Claude Code 格式兼容）
    └── kairo-observability     — OpenTelemetry 追踪（可选）

kairo-code-server
├── kairo-code-core
└── kairo-event-stream-ws       — Bridge SPI 的 WebSocket 传输层
```

## REPL 循环流程

REPL 遵循清晰的管道流程：

```
用户输入
    │
    ▼
命令分发 ──── 斜杠命令？ ──── 本地处理（如 /plan、/clear）
    │
    │（自然语言）
    ▼
Agent.call(userMessage)
    │
    ▼
┌─────────────────────────────────────────────┐
│           ReAct 循环（kairo-core）            │
│                                             │
│   推理 ──► 工具选择 ──► 执行                  │
│    ▲                       │                │
│    └────── 观察 ◄──────────┘                │
│                                             │
│   重复直到：产生答案 / 达到最大迭代次数          │
└─────────────────────────────────────────────┘
    │
    ▼
流式输出 ──► 终端渲染器
    │
    ▼
工具审批（如需要） ──► 用户确认 ──► 继续循环
    │
    ▼
最终响应 ──► 显示 + 持久化会话
```

每次 ReAct 循环迭代：

1. **推理阶段** -- 模型接收对话上下文，产生思考 + 工具调用（或最终答案）
2. **工具阶段** -- 选定的工具通过 `DefaultToolExecutor` 执行，受权限守卫约束
3. **观察阶段** -- 工具结果作为观察追加到上下文中
4. **压缩检查** -- 如果上下文超出 Token 预算，6 阶段压缩引擎（Snip、Micro、Collapse、Auto、Partial、CircuitBreaker）会自动缩减

## IDE 集成的 Bridge SPI

Bridge SPI 允许外部客户端（IDE、编辑器）通过传输层驱动 Kairo Code Agent：

```
┌──────────────┐     WebSocket      ┌───────────────────┐
│   VS Code    │◄──────────────────►│                   │
│   扩展        │                    │  kairo-code-server │
└──────────────┘                    │  （Bridge SPI）     │
                                    │                   │
┌──────────────┐     WebSocket      │  ┌─────────────┐  │
│  JetBrains   │◄──────────────────►│  │ kairo-code-  │  │
│   插件        │                    │  │   core       │  │
└──────────────┘                    │  └─────────────┘  │
                                    └───────────────────┘
┌──────────────┐     stdio (ACP)
│     Zed      │◄──────────────────► kairo-code-cli（ACP 模式）
└──────────────┘
```

Bridge SPI 将 IDE 请求（打开文件、执行命令、应用编辑）转换为 Kairo Agent 调用，并将结果流式回传。

## 会话生命周期

```
启动 CLI
    │
    ▼
从 .kairo/sessions/ 加载会话（如果存在）
    │
    ▼
初始化 Agent（模型提供者、工具、技能、Hook）
    │
    ▼
REPL 循环 ◄──────────────────────────┐
    │                                │
    ├── 处理输入                      │
    ├── 运行 Agent                    │
    ├── 持久化会话快照                 │
    └── 提示下一次输入 ────────────────┘
    │
    ▼
/exit ──► 保存最终会话 ──► 关闭
```

会话数据包括：
- 对话历史（带角色的消息）
- 当前会话的工具审批偏好
- 活跃的计划状态（如果处于计划模式）
- 会话期间创建的记忆条目
