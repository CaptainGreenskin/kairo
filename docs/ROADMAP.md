# Kairo Roadmap

> 最终版路线图。核心原则：骨架优先，每个版本有清晰的叙事焦点。

---

## 当前状态：v0.1.0-SNAPSHOT

已完成：
- ReAct 引擎 + 6 级上下文压缩管道
- 21 个内置工具 + 读写分区
- 三态权限模型 (ALLOWED/ASK/DENIED) + Human-in-the-Loop 审批
- Markdown 技能系统 + TriggerGuard 反污染
- 多 Agent 编排 (TaskBoard/TeamScheduler/MessageBus)
- Anthropic 深度集成 + OpenAI 兼容回退 (GLM/Qwen/GPT)
- Spring Boot Starter
- 安全加固（路径遍历防护、ReDoS 防护、命令注入修复、HTTPS 校验、敏感路径扩充）
- 945 个测试，0 失败
- 6 个 Demo

---

## v0.1.0 正式发布前 — API 表面积清理

公共 API 一旦发布到 Maven Central 就很难收回。发布前必须完成。

### ~~砍掉空壳接口~~ ✅ Done (v0.2.0)

以下接口已在 v0.2.0 中删除：

| 接口 | 理由 | 状态 |
|------|------|------|
| ~~`AgentLoop`~~ | 零引用，DefaultReActAgent 从未 import | ✅ 已删除 |
| ~~`MemorySearcher`~~ | 零引用，等 Vector Memory 时再定义 | ✅ 已删除 |
| ~~`StructuredOutput`~~ | 零引用，JSON 解析直接用 Jackson | ✅ 已删除 |
| ~~`McpInstructionProvider`~~ | 只有 noop() 调用，内联 `List::of` | ✅ 已删除 |

### 降级到内部实现

| 接口 | 理由 |
|------|------|
| `ModelHarness` | 从 kairo-api → kairo-core，只有一个实现，不是公共 SPI |
| `BoundaryMarker` | 从 kairo-api → kairo-core，只在 compaction 内部使用 |

### 确认保留

| 接口 | 理由 |
|------|------|
| `AgentFactory` | AgentSpawnTool + Spring Boot 自动配置在用 |
| `PlanFile` | 5 个文件引用，Plan 系统核心数据结构 |
| `ContextBuilder` | 有实现有测试 |

---

## v0.2.0 — 骨架加固

主题：让框架立得住。纯内部改进，不面向外部生态。

### P0 — Hook 结构化 + Session 生命周期

**结构化返回（扩展 PreReasoning/PostReasoning/PreCompact/PostCompact）：**
```java
HookResult.CONTINUE              // 继续执行
HookResult.SKIP                  // 跳过当前操作
HookResult.MODIFY(newInput)      // 修改输入（覆盖 model、temperature 等）
HookResult.INJECT(msg, source)   // 注入消息到上下文（必须带来源标记）
```

**INJECT 安全模型：**
- 注入的消息自动带 `metadata("hook_source", sourceHookName)`
- 注入的消息默认 `verbatimPreserved = true`（防止被压缩掉）
- 多个 Hook 的 INJECT 按 Hook 注册顺序追加，不互相覆盖
- 调试时可通过 `hook_source` 追踪消息来源

**Session 生命周期 Hook（新增）：**
```java
@OnSessionStart   // Agent 开始处理第一条消息（拿到初始配置）
@OnSessionEnd     // Agent 完成所有迭代（拿到完整对话历史 + token 统计）
@OnToolResult     // 每次工具调用完成（拿到工具名、成败、耗时）
```

优先级顺序：Router 先选模型 → Hook 后覆盖（Hook 是最后一道关卡）。

### P0 — Tracer 接口重设计

对齐 OTel Span 语义，但不引入 OTel 依赖：
```java
public interface Tracer {
    Span startAgentSpan(String agentName, Msg input);
    Span startIterationSpan(Span parent, int iteration);
    Span startToolSpan(Span parent, String toolName, Map<String, Object> input);

    void recordTokenUsage(Span span, int input, int output, int cacheRead, int cacheWrite);
    void recordToolResult(Span span, String toolName, boolean success, Duration duration);
    void recordCompaction(Span span, String strategy, int tokensSaved);
}
```

- 默认实现：`StructuredLogTracer`（JSON → SLF4J）+ Spring Actuator health indicator
- 注入方式：~~全局静态 `TracerRegistry` 已移除~~ → 使用 `AgentBuilder.tracer(Tracer)`
- 设计决策（已确定）：暴露 token/tool/compaction 三类指标

### P1 — 工具执行熔断

```java
// DefaultToolExecutor 内部，约 50 行
ConcurrentHashMap<String, AtomicInteger> consecutiveFailures;
// 连续 N 次失败（可配置，默认 3）→ 短路返回错误消息
// 区分可重试错误（网络超时）和不可重试错误（参数错误）
// 成功执行 → 重置计数器
```

### P1 — 多 Agent 基本容错

```java
// DefaultTaskBoard 扩展，约 30 行
// Agent 失败 → 任务状态改为 FAILED
// FAILED 任务自动重新入队为 PENDING（可配置重试次数，默认 2）
// 超过重试次数 → 标记为 ABANDONED
```

### P1 — 性能基线

不加独立模块，在 `kairo-core` 测试里加 `@Tag("performance")` 测试：
- 100 轮 Mock 对话的端到端耗时
- 每级压缩的触发延迟
- Token 估算 vs API 报告的偏差率

CI 默认跳过（`-DexcludedGroups=performance`），需要时手动跑。
Tracer 输出的 duration 数据直接喂给 benchmark。

---

## v0.3.0 — 生态连接

主题：让 Kairo 融入外部生态。

### MCP 集成

```
kairo-mcp/                    — 新模块（可选依赖）
├── McpClient                 — 连接 MCP Server（stdio/SSE）
├── McpToolAdapter            — MCP Tool → Kairo ToolDefinition 适配
├── McpToolExecutor           — 代理执行 MCP 工具调用
└── McpServerRegistry         — 管理多个 MCP Server 连接
```

**关键设计决策：**
- MCP 是扩展能力，不是核心依赖
- `kairo-core` 对 `kairo-mcp` 零编译期依赖
- 没有 MCP Server 时 Agent 正常工作
- MCP 连接失败时自动降级（跳过 MCP 工具，不阻塞 Agent 循环）

### OTel 集成

```
kairo-observability/          — 新模块（可选依赖）
└── OTelTracer implements Tracer  — 桥接到 OpenTelemetry SDK
```

用户加一个依赖就能接入 Jaeger/Grafana Tempo/Datadog。
处理 Reactor Context Propagation（响应式链路传递 trace context）。

### SSE 事件流

Spring Boot Controller 暴露 Agent 运行时事件：
- `reasoning.start` / `reasoning.end`
- `tool.start` / `tool.end` / `tool.error`
- `compaction.triggered` / `compaction.completed`

### OpenAPI 工具自动注册

解析 OpenAPI 3.0/3.1 spec → 自动生成 ToolDefinition。

---

## v0.4.0 — 生产级特性

根据真实用户反馈决定优先级。

| 项目 | 说明 |
|------|------|
| 自我改进机制 | 对话回顾、自动 Skill 提取、Memory 沉淀 |
| 多租户隔离 | Agent 级资源配额、租户级 MemoryStore 隔离 |
| Memory 分类重设计 | 参考 claude-code-best 的内容类型分类（user/feedback/project/reference） |

已明确不做：
- Workflow Checkpoint — SessionSerializer + FileMemoryStore + TaskBoard 已覆盖恢复需求，完整 Workflow 是分布式编排系统的领域
- Smart Model Routing — Hook 结构化完成后，用户可通过 `@PreReasoning` + `HookResult.MODIFY` 自行实现路由逻辑，框架不预设策略

---

## 模块演进

```
v0.1.0                v0.2.0 (骨架加固)      v0.3.0 (生态连接)      v0.4.0 (生产级)
kairo-api             kairo-api (精简)       kairo-api              kairo-api
kairo-core            kairo-core             kairo-core             kairo-core
kairo-tools           kairo-tools            kairo-tools            kairo-tools
kairo-multi-agent     kairo-multi-agent      kairo-multi-agent      kairo-multi-agent
kairo-spring-boot-    kairo-spring-boot-     kairo-spring-boot-     kairo-spring-boot-
  starter               starter                starter                starter
kairo-demo            kairo-demo             kairo-demo             kairo-demo
                                             + kairo-mcp            kairo-mcp
                                             + kairo-observability  kairo-observability
```

---

## 设计原则

1. **骨架优先** — 先让框架立得住，再扩展功能
2. **嵌入式优先** — Kairo 是库，不是平台。`import` 就能用
3. **SPI 可扩展** — 每个核心能力都有接口，用户可以替换任何实现
4. **响应式非阻塞** — 所有 I/O 操作基于 Reactor
5. **渐进式采用** — 从 Hello World 到多 Agent 编排，复杂度按需引入
6. **不预设策略** — 框架提供能力（Hook、Tracer、MemoryStore），不预设使用方式
7. **扩展模块完全可选** — MCP、OTel 等扩展模块是可选依赖，连接失败不影响核心功能

---

## 里程碑

| 版本 | 主题 | 关键交付物 |
|------|------|-----------|
| v0.1.0 | 首次发布 | API 清理 → Maven Central |
| v0.2.0 | 骨架加固 | Hook 结构化 + Tracer 重设计 + 熔断 + 容错 + 性能基线 |
| v0.3.0 | 生态连接 | MCP + OTel + SSE + OpenAPI 工具 |
| v0.4.0 | 生产级 | 自我改进 + 多租户 + Memory 重设计 |
