# 特性

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

## 演示示例

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

## 能力验证（轻量轨道）

为了在 `v1.0` 之前把 Agent 能力从“描述”变成“可复现证据”，Kairo 新增了轻量 benchmark 基线目录 `benchmarks/`：

- `benchmarks/scenarios/v0-lite-scenarios.jsonl`：20 个代表性场景
- `benchmarks/metrics-schema.json`：统一结果结构（状态、延迟、Token、安全决策）
- `benchmarks/README.md`：运行与聚合说明

该轨道用于持续积累跨版本证据，重点覆盖：

- 工具调用正确性
- 长任务稳定性
- 安全治理有效性
- 成本/延迟趋势可见性
