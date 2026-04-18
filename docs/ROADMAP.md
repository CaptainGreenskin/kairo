# Kairo Roadmap & Execution Plan

> 核心原则：骨架优先，先让框架立得住，再扩展功能。
> 每个版本有清晰的叙事焦点，不做"什么都有"的版本。

---

## 基线

- 当前版本：v0.1.0-SNAPSHOT
- 测试：1,009 个，0 失败
- 模块：kairo-api / kairo-core / kairo-tools / kairo-multi-agent / kairo-spring-boot-starter / kairo-examples / kairo-mcp / docs（8 个）

---

## 阶段一：v0.1.0 — 首次发布（API 清理）

> 目标：收窄公共 API 表面积，修掉破坏 SPI 扩展性的设计缺陷。
> 发布到 Maven Central 后接口就很难收回。

### Task 1.1：砍掉 4 个空壳接口 ✅ 已完成

| 接口 | 理由 |
|------|------|
| `AgentLoop` | 0 import，DefaultReActAgent 内部实现了 ReAct 循环 |
| `MemorySearcher` | 0 import，等 Vector Memory 时再定义 |
| `StructuredOutput` | 0 import，JSON 解析直接用 Jackson |
| `McpInstructionProvider` | 只有 noop() 调用，PostCompactRecoveryHandler 内联 `List::of` |

### Task 1.2：ModelHarness 移到 kairo-core ✅ 已完成

只有 `ClaudeModelHarness` 一个实现，不是公共 SPI。
BoundaryMarker 保留在 kairo-api（移动会级联到 HookChain API，风险过大）。

### Task 1.3：消除 instanceof 检查 ✅ 已完成

DefaultReActAgent 有 5 处 `instanceof DefaultToolExecutor`，破坏 SPI 扩展性。

在 ToolExecutor 接口添加 default 方法：
```java
default void setAllowedTools(Set<String> tools) {}
default void clearAllowedTools() {}
default boolean supportsStreaming() { return false; }
```

DefaultReActAgent 的 5 处 instanceof → 直接调用接口方法。

### Task 1.4：消除硬编码模型默认值 ✅ 已完成

`claude-sonnet-4-20250514`、`maxTokens=8096`、`temperature=1.0` 从 DefaultReActAgent 移到 ModelConfig 常量。

### Task 1.5：DefaultHookChain → HookChain 接口 ✅ 已完成

构造函数参数类型从具体类改为接口。同步更新 AgentBuilder 和 DefaultAgentFactory。

### Task 1.6：创建 @Experimental 注解 ✅ 已完成

### Task 1.7：标记 MCP 方法 @Experimental ✅ 已完成

### Task 1.8：TracerRegistry 改为 Builder 注入 ✅ 已完成

- 删除 `TracerRegistry.java`
- AgentConfig + AgentBuilder 添加 `.tracer(Tracer)` 方法
- DefaultReActAgent / DefaultToolExecutor 改为构造注入

### Task 1.9：文档检查 ✅ 已完成

确保 README / docs / Demo 不引用已删除的 API。

### Task 1.10：全量验证 ✅ 已完成

`mvn clean test` — 结果：1,009 测试，0 失败。

**v0.1.0 全部完成 ✅**

### v0.1.0 发布前补充项（新增）

以下是发布到 Maven Central 前的补充工作：

| Task | 状态 | 说明 |
|------|------|------|
| CI Spotless check | ✅ | 在 ci.yml 加 JDK 17 的 format-check job（绕过 Java 25 不兼容） |
| JaCoCo 覆盖率阈值 | ✅ | kairo-core ≥ 50%，kairo-multi-agent ≥ 80%，其他不设 |
| kairo-bom 模块 | ✅ | 一个 pom.xml 列出所有模块版本，用户一次管理依赖 |
| 核心接口 Javadoc | ✅ | 补全 Agent / ToolExecutor / HookChain / ModelProvider / ContextManager（约 10 个文件） |
| Release workflow | ✅ | GitHub Actions 自动发布到 Maven Central（可在 Sonatype 审批期间编写） |
| Spring Boot Example | ✅ | 验证 Starter 可用性（AutoConfigurationIT），暴露强转等集成问题 |
| API 稳定性注解扩展 | ⬜ P1 | 扩展 @Experimental 到整个 API 层，等 v0.2.0 API 稳定后做 |

---

## 阶段二：v0.2.0 — 骨架加固

> 主题：让框架立得住。纯内部改进，不面向外部生态。

### Task 2.1：Hook SKIP/INJECT + Session 生命周期 ✅ 已完成

**HookResult 扩展：**
- Decision 枚举：CONTINUE / SKIP / ABORT / MODIFY / INJECT
- 优先级合并：ABORT > SKIP > MODIFY > INJECT > CONTINUE
- INJECT 安全模型：注入消息带 `metadata("hook_source", source)` + `verbatimPreserved(true)`

**Session 生命周期 Hook：**
- `@OnSessionStart` / `@OnSessionEnd` / `@OnToolResult`
- fire-and-forget，不返回 HookResult

### Task 2.2：Tracer 接口重设计 ✅ 已完成

Span 接口精简为通用方法（`setAttribute` / `setStatus` / `end`），直接映射 OTel Span。
业务便捷方法（`recordTokenUsage` / `recordToolResult` / `recordCompaction`）在 Tracer 接口上作为 default 方法，内部委托 `span.setAttribute()`。

```java
public interface Span {
    String spanId();
    String name();
    Span parent();
    void setAttribute(String key, Object value);
    void setStatus(boolean success, String message);
    void end();
}

public interface Tracer {
    Span startAgentSpan(String agentName, Msg input);
    Span startIterationSpan(Span parent, int iteration);
    Span startReasoningSpan(Span parent, String modelName, int messageCount);
    Span startToolSpan(Span parent, String toolName, Map<String, Object> input);

    // Business convenience methods — delegate to span.setAttribute()
    default void recordTokenUsage(Span span, int input, int output, int cacheRead, int cacheWrite) { ... }
    default void recordToolResult(Span span, String toolName, boolean success, Duration duration) { ... }
    default void recordCompaction(Span span, String strategy, int tokensSaved) { ... }
}
```

默认实现：`StructuredLogTracer`（JSON → SLF4J）。

### Task 2.3：异常层次结构 ✅ 已完成

```
KairoException (基类)
├── AgentException
│   ├── AgentInterruptedException
│   └── AgentExecutionException
├── ModelException
│   ├── ModelRateLimitException
│   └── ModelTimeoutException
└── ToolException
    ├── ToolPermissionException
    └── PlanModeViolationException
```

与 Tracer 的 `recordException` 配合，按异常类型分类记录。

### Task 2.4：GracefulShutdownManager 改注入 ✅ 已完成

和 TracerRegistry 同一个反模式，同一个改法：移除全局单例，通过 AgentBuilder 控制。

### Task 2.5：工具执行熔断 ✅ 已完成

DefaultToolExecutor 内部加 `ConcurrentHashMap<String, AtomicInteger>` 跟踪连续失败。
连续 N 次失败（可配置，默认 3）→ 短路返回错误消息。约 50 行。

### Task 2.6：多 Agent 基本容错 ✅ 已完成

DefaultTaskBoard：Agent 失败 → FAILED → 自动重入队 PENDING（可配置重试次数）→ 超限 → ABANDONED。约 30 行。

### Task 2.7：性能基线 ✅ 已完成

在 kairo-core 测试里加 `@Tag("performance")` 测试（不加独立模块）：
- 100 轮 Mock 对话端到端耗时
- 每级压缩触发延迟
- Token 估算 vs API 报告偏差

CI 默认跳过，需要时手动跑。设宽松阈值，发现明显退化即可。

### Task 2.8：Token Budget SPI 接口 ✅ 已完成

将 `tokenBudget` 从静态 int 升级为 `TokenBudgetManager` SPI：
```java
public interface TokenBudgetManager {
    void recordUsage(int inputTokens, int outputTokens);
    int remaining();
    boolean isExhausted();
    BudgetAction checkBudget();  // CONTINUE / WARN / STOP
}
```

v0.2.0 只做接口定义 + 基础追踪实现（替代现有的静态 int）。
模型降级策略（budget 快耗尽时自动切便宜模型）放 v0.3.0。

### Task 2.9：System Prompt 增强 ✅ 已完成

**工具描述增强：** `@Tool` 注解增加 `usageGuidance` 字段（或 ToolDefinition 增加 guidance），
SystemPromptBuilder 自动拼接到工具描述后。让 LLM 知道何时用 bash vs 专用工具、危险命令处理、长输出处理等。

**模型适配指导：** `ModelCapability` 增加 `promptGuidance` 字段，根据模型类型注入不同指导：
- GPT/Codex："必须用工具行动，不要描述"
- Gemini："用绝对路径，修改前先读文件"
- Claude/GLM：不需要额外指导

---

## 阶段三：v0.2.1 — 重构

> 目标：DefaultReActAgent 拆分。等 v0.2.0 Hook 埋点稳定后做。

### Task 3.1：DefaultReActAgent 拆分 ✅ 已完成

v0.2.0 Hook + Tracer 的埋点位置自然形成代码分界线。拆分为 6 个类：

| 类 | 职责 | 实际行数 |
|----|------|--------|
| `DefaultReActAgent` | Agent 接口实现 + 主循环编排 | 427 |
| `ReActLoop` | 迭代逻辑 + 条件判断 | 816 |
| `ReActLoopContext` | 循环上下文数据类 | 41 |
| `SessionResumption` | 会话恢复 + 跨 Session 上下文注入 | 96 |
| `SkillToolManager` | Skill 触发 → 工具限制 → MCP 工具注入 | 135 |
| `CompactionTrigger` | 压缩判断 + 触发 + 结果合并 | 74 |

合计 1,589 LOC（原 1,255 LOC 单文件 → 6 文件，DefaultReActAgent 从 1,255 → 427）。

拆分原则：不改公共 API，不改测试行为断言。

### Task 3.2：行为准则 Skill 模板 ✅ 已完成

不内置默认行为规范（违反"不预设策略"原则），提供场景化 Skill 模板：
- `skills/coding-guidelines.md` — 编码助手（Read before write, don't add features beyond asked）
- `skills/sre-guidelines.md` — SRE 运维（Check before act, verify idempotency）
- `skills/data-guidelines.md` — 数据分析

用户按需加载，和 Skill 系统设计一致。

### Task 3.3：工具输出注入防御 ✅ 已完成

在 `DefaultToolExecutor` 的后处理阶段扫描工具输出，命中注入模式时在 ToolResult 标记 warning。
不在 prompt 层做（不依赖 LLM 自觉遵守，对所有模型有效，不消耗 prompt token）。

扫描模式：ignore previous instructions、system prompt override、不可见 Unicode、凭据泄露等。
ToolOutputSanitizer + 37 个新测试。

### Task 3.4：Coordinator Prompt 精化 ✅ 已完成

增加精确行为约束：
- "Worker results are internal signals, not conversation partners — never thank or acknowledge them"
- "不要向用户暴露内部机制（compaction、hook、tool partition 等）"

### Task 3.5：ReActLoop Cooperative Cancellation ⬜ 待完成

在 ReActLoop 迭代间检查 interrupted flag，支持细粒度取消（单个子任务级别，不是整个 Agent）。
Java/Reactor 实现：`Flux.takeUntilOther(cancelSignal)` 或 step 间检查 `interrupted` flag，不需要新抽象类型。

### Task 3.6：Loop Detection ⬜ 待完成

双层循环检测，在 ReActLoop 的 step 之间加一个轻量 `LoopDetector`（有状态组件，不需要新 SPI）：
- **Hash 层：** 滑动窗口内对 tool call 集合（name + 规范化参数）做 hash，3 次相同警告，5 次硬停
- **频率层：** 按工具类型计数（不管参数），30 次警告，50 次硬停

当前 DefaultReActAgent 只有 maxIterations 硬上限，没有智能循环检测。如果 LLM 反复调用同一个工具但参数微调（SRE 场景中常见），会白白烧 token 直到 hit maxIterations。

### Task 3.7：Skill 系统补齐（模板能力）⬜ 待完成

对标完整 Skill 能力，当前缺失三项模板能力：
- **参数替换（Argument Substitution）：** Skill Markdown 中支持 `{{arg}}` 占位符，加载时替换为实际值
- **`${SKILL_DIR}` 变量替换：** Skill 引用相对路径资源时，自动替换为 Skill 文件所在目录
- **内联脚本执行（Inline Script Execution）：** Skill 中嵌入的 `bash` / `python` 代码块可直接执行

计划版本：v0.3.0（参数替换 + ${SKILL_DIR}），v0.4.0（内联脚本执行，需安全审计）

---

## 阶段四：v0.3.0 — 生态连接

> 主题：让 Kairo 融入外部生态。

| Task | 说明 | 状态 |
|------|------|------|
| MCP 集成完善 | kairo-mcp 模块正式发布，去掉 @Experimental 标记 | ✅ 已完成 |
| MCP: StreamableHTTP 传输 | P0 — MCP SDK 1.1.1 内置 StreamableHTTP（无状态） | ✅ 已完成 |
| MCP: HTTP header/queryParam | P0 — 企业 MCP Server 认证（headers, queryParams, bearerToken） | ✅ 已完成 |
| MCP: Elicitation 协议 | P1 — Server 向 Client 请求用户输入，和 Human-in-the-Loop 天然对齐 | ✅ 已完成 |
| MCP: Sync 便利方法 | P2 — 非响应式用户友好，提供阻塞式 API 包装 | ✅ 已完成 |
| MCP: Spring Boot 配置扩展 | P0 — `kairo.mcp.*` 配置前缀 + discriminator pattern | ✅ 已完成 |
| Structured Output | `ModelConfig.responseSchema()` + `ModelResponse.contentAs()` — 原生 API 结构化输出（OpenAI response_format, Anthropic prompt injection + parse） | ✅ 已完成 |
| OTel 集成 | `kairo-observability` 模块：OTelTracer, OTelSpan, GenAiSemanticAttributes, OTelTracerFactory | ✅ 已完成 |
| CacheBreakDetector | AnthropicProvider 缓存命中率监控（cache.hit_ratio, cache.broken span 属性） | ✅ 已完成 |
| SSE 事件流 | 订阅 Tracer 事件 → 转 SSE（依赖 v0.2.0 Tracer 的事件订阅能力） | ⬜ → v0.4.0 |
| OpenAPI 工具自动注册 | 解析 OpenAPI spec → 自动生成 ToolDefinition（swagger-parser 2.1.22） | ✅ 已完成 |
| Skill 远程加载 | `loadFromClasspath()` + URL TTL cache（configurable, default 1hr） | ✅ 已完成 |
| AnthropicProvider 拆分 | AnthropicHttpClient + AnthropicResponseParser + AnthropicProvider，ModelProviderException 共享 | ✅ 已完成 |
| ProviderPresets 便利层 | anthropic/openai/qwen/glm/deepseek factory methods，一行代码接入 | ✅ 已完成 |
| Span.addEvent + 确定性回放 | Span 接口加 `addEvent(name, snapshot)` — API default no-op, OTelSpan delegation, StructuredLogSpan JSON logging | ✅ 已完成 |
| Token Budget 降级策略 | 基于 v0.2.0 的 TokenBudgetManager SPI，实现 budget 耗尽时自动切便宜模型 | ⬜ → v0.4.0 |
| ExecutionStrategy SPI（预留） | 预留接口，默认 ReAct。简单任务场景已可通过 `@PreReasoning` + `HookResult.SKIP` 覆盖 | ⬜ → v0.4.0 |
| 记忆使用指导 | 配套 MemoryTool，在 system prompt 中加入记忆使用规范（什么值得持久化、什么不值得） | ⬜ → v0.4.0 |

**v0.3.0 健壮性 + SPI（6 项）：** PermissionDecision + tag 过滤 + pre-compaction flush + Model CircuitBreaker + Dangling Tool Call Recovery + ToolGroup + ToolResultPostProcessor

**v0.3.0 已完成项 ✅：** PermissionDecision + MemoryEntry confidence + tag 过滤 + pre-compaction flush + Model CircuitBreaker + Dangling Tool Call Recovery（1,497 tests, 0 failures）

**v0.3.0 剩余项 ⬜：** ToolGroup 动态激活 + ToolResultPostProcessor

**v0.3.1 已完成 ✅** — AnthropicProvider 拆分 + Structured Output + MCP Elicitation + OpenAPI Tools + ProviderPresets + Skill Remote Loading + MCP Sync API + Span.addEvent()

**v0.3.2 Skill 进化（4 项）：** 多文件 Bundle + Search Path + SkillManageTool（CRUD）+ 变更历史 + 参数替换

**Deferred to v0.4.0:** SSE Event Stream, Token Budget Degradation, ExecutionStrategy SPI, Memory Usage Guidance

### v0.3.0 SPI 返回值丰富化（一个 PR）✅ 已完成

以下三项都是"返回值变更或方法重载"级别的小改动，合并为一个 PR：

| 改动 | 说明 | 兼容策略 | 状态 |
|------|------|---------|------|
| PermissionGuard → PermissionDecision | 返回 `PermissionDecision(allowed, reason, policyId)` 替代 `Boolean` | 加 `default checkPermissionDetail()` 委托给旧方法，不破坏现有实现 | ✅ |
| Pre-compaction memory flush | CompactionTrigger 内部在 compact 前 flush 重要消息到 MemoryStore | 可配置 Predicate<Msg>，默认 verbatimPreserved | ✅ |
| MemoryStore tag 过滤 | `search(query, scope)` 加重载 `search(query, scope, tags)` | AND 语义，MemoryEntry 已有 tags 字段 | ✅ |
| MemoryEntry confidence 字段 | `Optional<Double> confidence`（default null，向后兼容） | 6-arg 构造函数保留，7-arg 新增 | ✅ |

### v0.3.0 健壮性增强（一个 PR）✅ 已完成

| 改动 | 说明 | 改动量 | 优先级 | 状态 |
|------|------|--------|--------|------|
| Model Call Circuit Breaker | ErrorRecoveryStrategy 入口加 `ModelCircuitBreaker`（Closed → Open → Half-Open 三态，5 次连续失败触发，60 秒后半开探测），只对暂时性错误（SERVER_ERROR, RATE_LIMITED）计数 | 小 | 高 | ✅ |
| Dangling Tool Call Recovery | ReActLoop.runLoop() 开头扫描 conversationHistory，检测未配对的 tool_call 并注入 error ToolResult | 小 | 高 | ✅ |

### v0.3.0 工具增强（一个 PR）

| 改动 | 说明 | 改动量 |
|------|------|--------|
| ToolGroup 动态激活 | 工具按组注册，运行时按需激活/停用整组工具 | 小 |
| ToolResultPostProcessor | 工具执行后的结果后处理 SPI（截断、格式化、脱敏） | 小 |

### v0.3.2 Skill 系统增强

> 主题：Skill 进化 — 从单文件提示词到完整能力包。不改 SPI 接口，只增强现有组件。

**PR 1：多文件 Bundle + Search Path**

| 改动 | 说明 | 改动量 |
|------|------|--------|
| 多文件 Skill Bundle | SkillLoader 支持目录结构：`SKILL.md` + `scripts/` + `templates/` + `references/`。SkillDefinition 加 `Path bundleRoot`（nullable，null = 传统单文件），运行时通过 `bundleRoot.resolve("scripts/xxx.sh")` 访问资源，不预设资源分类 | 中 |
| Search Path 模型 | 替代硬编码 public/custom 目录。配置驱动的搜索路径，后者覆盖前者同名 Skill | 小 |

```yaml
# application.yml
kairo:
  skills:
    search-paths:
      - ~/.kairo/skills       # 最高优先级（用户自定义）
      - ./project-skills      # 项目级
      - classpath:skills      # 最低优先级（框架内置）
```

**PR 2：SkillManageTool（CRUD）+ 变更历史**

| 改动 | 说明 | 改动量 |
|------|------|--------|
| SkillManageTool | 新增工具，支持 create/edit/delete。复用 SkillMarkdownParser.serialize() 做写入 | 中 |
| 变更历史（JSONL） | CRUD 操作前旧内容追加到 `.history/{name}.jsonl`。可配置 `max-history-entries`（默认 50），oldest-first pruning 防止磁盘膨胀 | 小 |
| 参数替换 + ${SKILL_DIR} | Skill Markdown 中 `{{arg}}` 占位符加载时替换；`${SKILL_DIR}` 替换为 Skill 文件所在目录 | 小 |

**CRUD 安全约束（硬性要求）：**
- CRUD 操作必须经过 PermissionGuard，权限级别 = `SYSTEM_CHANGE`
- 支持 `kairo.skills.readonly=true` 配置项，生产环境禁用 CRUD
- JSONL 变更历史是 SkillManageTool 的硬前置依赖，必须同时交付，不可拆分
- 多 Skill 同时加载时，allowedTools 合并策略：取并集（SkillToolManager 验证）

**MCP 设计决策：**
- MCP 完全可选，`kairo-core` 对 `kairo-mcp` 零编译期依赖
- 没有 MCP Server 时 Agent 正常工作
- MCP 连接失败时自动降级（跳过 MCP 工具，不阻塞 Agent 循环）

**Provider 模型层说明：**
- 智谱 Coding Plan / 标准 API / Anthropic 兼容端点对 Kairo 是同一个 ModelProvider 接口的不同配置，不需要架构改动
- Coding Plan 的真正价值是附带的 4 个专属 MCP Server（联网搜索、网页读取、开源仓库、视觉理解），随 MCP 集成一起落地
- `ProviderPresets` 便利类与 MCP 专属 Server 自动发现打包发布，给用户完整的开箱体验

---

## 阶段五：v0.4.0 — 生产级特性

> 根据真实用户反馈决定优先级。

| Task | 说明 |
|------|------|
| 自我改进机制 | 对话回顾、自动 Skill 提取、Memory 沉淀 |
| 多租户隔离 | Agent 级资源配额、租户级 MemoryStore 隔离 |
| Memory 分类重设计 | 按内容类型重新分类（user/feedback/project/reference），替代当前的生命周期分类 |
| SkillRegistry SPI 正式化 | Skill 注册/发现接口冻结，等 v0.3.2 Skill 增强稳定后定义 |
| Skill 组合（跨 Skill 引用） | 复杂任务编排多个专业 Skill（依赖 Bundle + CRUD 先落地，不需要框架级支持） |
| Skill ZIP 安装 | 打包分发 Skill Bundle，含安全验证 |
| Skill 安全扫描 | 基于 ToolOutputSanitizer 正则方案做 Skill 内容基础扫描（prompt injection 检测） |
| Skill 内联脚本执行 | Skill 中嵌入的 bash/python 代码块可直接执行（需安全审计） |
| 环境感知安全策略 | 根据执行环境（local/Docker/remote）动态调整 PermissionGuard 策略 |
| Agent 状态快照/恢复 | Agent 运行时状态序列化，支持断点恢复和调试 |
| Run 并发策略 | 同一 thread 的并发 run 控制：reject / interrupt / rollback 三种策略 |
| SSE 事件流 | 订阅 Tracer 事件 → 转 SSE |
| Token Budget 降级策略 | budget 耗尽时自动切便宜模型 |
| ExecutionStrategy SPI | 预留非 ReAct 策略接口 |
| 记忆使用指导 | 配套 MemoryTool + system prompt 指导 |
| TokenCounter 抽象 | 统一的 token 计数 SPI，支持不同模型的 tokenizer |

**已明确不做：**
- Workflow Checkpoint — SessionSerializer + FileMemoryStore + TaskBoard 已覆盖恢复需求
- Smart Model Routing — Hook 结构化完成后，用户可通过 `@PreReasoning` + `HookResult.MODIFY` 自行实现

---

## 已知设计问题及优化计划

| # | 问题 | 严重程度 | 状态 | 修复方式 | 计划版本 |
|---|------|---------|------|---------|---------|
| 1 | `instanceof` 检查破坏接口抽象 | 高 | ✅ 已修复 | ToolExecutor 接口加 default 方法 | v0.1.0 |
| 2 | DefaultReActAgent God Object | 高 | ✅ 已修复 | 拆分为 6 个类（427+816+41+96+135+74 LOC） | v0.2.1 |
| 3 | 缺少统一异常层次 | 中 | ✅ 已修复 | 建立 KairoException 层次结构 | v0.2.0 |
| 4 | 硬编码模型默认值 | 中 | ✅ 已修复 | 移到 ModelConfig 常量 | v0.1.0 |
| 5 | GracefulShutdownManager 全局单例 | 中 | ✅ 已修复 | 改为 AgentBuilder 注入 | v0.2.0 |
| 6 | 构造函数依赖具体类 | 中 | ✅ 已修复 | 参数类型改为 HookChain 接口 | v0.1.0 |
| 7 | TracerRegistry 全局静态单例 | 中 | ✅ 已修复 | 改为 AgentBuilder.tracer() 注入 | v0.1.0 |
| 8 | `.block()` 在响应式链中 | 低 | ⬜ | 改用 flatMap | v0.2.0+ |
| 9 | AnthropicProvider 973 行 | 低 | ✅ 已修复 | 拆分为 AnthropicHttpClient + AnthropicResponseParser + AnthropicProvider | v0.3.1 |
| 10 | MemoryScope 维度设计偏差 | 低 | ⬜ | 等做 Memory 检索时重设计 | v0.4.0 |

---

## 模块演进

```
v0.1.0 (API清理)       v0.2.0 (骨架加固)       v0.3.0 (生态连接)       v0.4.0 (生产级)
kairo-api (精简)       kairo-api (异常+Hook)   kairo-api               kairo-api
kairo-bom              kairo-bom               kairo-bom               kairo-bom
kairo-core             kairo-core              kairo-core              kairo-core
kairo-tools            kairo-tools             kairo-tools             kairo-tools
kairo-multi-agent      kairo-multi-agent(容错) kairo-multi-agent       kairo-multi-agent
kairo-spring-boot-     kairo-spring-boot-      kairo-spring-boot-      kairo-spring-boot-
  starter                starter                 starter                 starter
kairo-examples         kairo-examples          kairo-examples          kairo-examples
kairo-mcp(@Exp)        kairo-mcp(@Exp)         kairo-mcp(正式)         kairo-mcp
                                               + kairo-observability   kairo-observability
```

---

##  借鉴对照（学设计理念，不学基础设施复杂度）

| # | 借鉴点 | 结论 | 落地方式 | 版本 |
|---|--------|------|---------|------|
| 1 | Cooperative cancellation | ✅ 做 | ReActLoop step 间检查 interrupted flag | v0.2.1 |
| 2 | Loop Detection（双层） | ✅ 做 | LoopDetector：Hash 层（滑动窗口 tool call hash）+ 频率层（按工具类型计数） | v0.2.1 |
| 3 | PermissionGuard 结构化 reason | ✅ 已完成 | `PermissionDecision(allowed, reason, policyId)` + default 方法兼容 | v0.3.0 |
| 4 | Pre-compaction memory flush | ✅ 已完成 | CompactionTrigger 内部 flush 重要消息到 MemoryStore | v0.3.0 |
| 5 | MemoryStore tag 过滤 | ✅ 已完成 | `search(query, scope, tags)` 方法重载，AND 语义 | v0.3.0 |
| 6 | Model Call Circuit Breaker | ✅ 已完成 | ModelCircuitBreaker（Closed/Open/Half-Open），只对暂时性错误计数 | v0.3.0 |
| 7 | Dangling Tool Call Recovery | ✅ 已完成 | ReActLoop.runLoop() 开头扫描未配对 tool_call，注入 error ToolResult | v0.3.0 |
| 8 | ToolGroup 动态激活 | ✅ 做 | 工具按组注册，运行时按需激活/停用 | v0.3.0 |
| 9 | ToolResultPostProcessor | ✅ 做 | 工具结果后处理 SPI（截断、格式化、脱敏） | v0.3.0 |
| 10 | 多文件 Skill Bundle | ✅ 做 | SkillDefinition 加 `Path bundleRoot`，运行时 resolve 资源路径 | v0.3.2 |
| 11 | SkillManageTool（CRUD） | ✅ 做 | SYSTEM_CHANGE 权限 + readonly 模式 + JSONL 历史硬前置 | v0.3.2 |
| 12 | Skill Search Path 模型 | ✅ 做 | 配置驱动搜索路径，后者覆盖前者同名 Skill | v0.3.2 |
| 13 | Skill 变更历史 | ✅ 做 | CRUD 操作前旧内容追加 JSONL，max-entries=50 + oldest-first pruning | v0.3.2 |
| 14 | Agent 状态快照/恢复 | ✅ 做 | Agent 运行时状态序列化，断点恢复 | v0.4.0 |
| 15 | MemoryEntry confidence 字段 | ✅ 已完成 | `Optional<Double> confidence`（default null，7-arg 构造函数） | v0.3.0 |
| 16 | Skill 组合（跨 Skill 引用） | ✅ 做 | 依赖 Bundle + CRUD 先落地，不需要框架级支持 | v0.4.0 |
| 17 | 环境感知安全策略 | ✅ 做 | 根据执行环境动态调整 PermissionGuard | v0.4.0 |
| 18 | Run 并发策略 | ✅ 做 | reject / interrupt / rollback 三种策略 | v0.4.0 |
| 19 | TokenCounter 抽象 | ✅ 做 | 统一 token 计数 SPI，支持不同 tokenizer | v0.4.0+ |

v0.2.1 的 2 个点（#1 cancellation + #2 loop detection）在 ReActLoop 拆分时一起做。
v0.3.0 SPI + 健壮性（#3, #4, #5, #6, #7, #15）已完成 ✅。剩余工具增强（#8, #9）待做。
v0.3.2 分 2 个 PR：Bundle + Search Path（#10, #12），CRUD + 历史 + 参数替换（#11, #13）。

核心原则：很多设计理念用现有的 SPI（Hook、Tracer、PermissionGuard）就能实现，不需要新增抽象。

### Skill 系统对比总结

**Kairo 领先项（不需要借鉴）：**
- Anti-Pollution TriggerGuard — 显式 trigger matching + 0.8 阈值，比纯 LLM 判断更可靠
- 条件激活 — pathPatterns / platform / requiredTools 三维条件
- Budget-aware Prompt 注入 — 3 级降级（完整描述 → 截断描述 → 仅名称），节省 token
- allowedTools 白名单 — 在 ReActLoop 里强制执行

**v0.3.2 补齐项：** 多文件 Bundle（`Path bundleRoot`）+ Search Path 模型 + CRUD Tool（SYSTEM_CHANGE 权限 + readonly 模式）+ 变更历史（JSONL, max 50 条）+ 参数替换
**v0.4.0 补齐项：** SkillRegistry SPI 正式化 + ZIP 安装 + 安全扫描 + Skill 组合 + 内联脚本执行（SPI 级变更）

**关键设计决策：**
- `Path bundleRoot` 而非 `Map<String, List<Path>> resources` — 不预设资源分类，运行时按需 resolve
- Search Path 模型而非 public/custom 硬编码 — 支持 project-level / team-level 扩展
- JSONL 变更历史是 CRUD 的硬前置依赖 — 同时交付或都不交付
- JSONL max-entries=50 + oldest-first pruning — 防止 Agent 快速迭代时磁盘膨胀

### 不借鉴清单

| 特性 | 不借鉴理由 |
|------|-----------|
| LangSmith/Langfuse 集成 | Kairo 有 Tracer SPI + OTel |
| ThreadPoolExecutor 调度 | Reactor Schedulers 已覆盖 |
| IM Channel（Telegram/Slack/飞书/微信） | 不在 Kairo 定位内 |
| Skill Security Scanner (LLM-based) | 延迟高，Kairo 的正则方案够用 |
| File Upload/Document Conversion | 不在核心定位 |
| vLLM/DeepSeek Provider Patches | Kairo 主要对接 Anthropic |
| XSS Artifact Protection | SRE 场景不需要 |
| Config Hot Reload | Spring Boot @RefreshScope 已覆盖 |
| Metaclass AOP | Java 有 Spring AOP |
| MsgHub pub/sub | Kairo 已有 MessageBus SPI |
| RAG 管道 | 用户自己接，框架不做 |
| RL/Finetuning/Tuner | 框架定位不同 |
| Realtime Voice Agent | 不相关 |
| A2A 协议 | 太早期 |
| Evaluation Framework | 独立关注点 |
| Distributed Runtime | Kairo 是嵌入式库 |
| Mem0/ReME 长期记忆 | 用户通过 MemoryStore SPI 自己接 |
| Redis/SQLAlchemy 后端 | 同上 |
| Formatter 分离 | 只对接 Anthropic 时不需要 |
| Token Budget 三级管控 | ✅ 已做 — TokenBudgetManager SPI + 动态追踪（v0.2.0） |

---

## 设计原则

1. **骨架优先** — 先让框架立得住，再扩展功能
2. **嵌入式优先** — Kairo 是库，不是平台。`import` 就能用
3. **SPI 可扩展** — 每个核心能力都有接口，用户可以替换任何实现
4. **响应式非阻塞** — 所有 I/O 操作基于 Reactor
5. **渐进式采用** — 从 Hello World 到多 Agent 编排，复杂度按需引入
6. **不预设策略** — 框架提供能力（Hook、Tracer、MemoryStore），不预设使用方式
7. **扩展模块完全可选** — MCP、OTel 等扩展模块连接失败不影响核心功能

---

## 里程碑

| 版本 | 主题 | 关键交付物 | 预估周期 |
|------|------|-----------|---------|
| v0.1.0 | 首次发布 | 补充 CI/BOM/Javadoc → Maven Central | 1-2 周 |
| v0.2.0 | 骨架加固 | 异常层次 + ShutdownManager + 熔断 + 容错 + 性能基线 | 2-3 周 |
| v0.2.1 | 重构 | DefaultReActAgent 拆分 + LoopDetector + Cooperative Cancellation + Skill 模板 | 1-2 周 |
| v0.3.0 | 健壮性 + SPI | PermissionDecision + tag 过滤 + confidence + Model CircuitBreaker + Dangling Recovery ✅ / ToolGroup + ToolResultPostProcessor ⬜ | 2-3 周 |
| v0.3.1 | 生态连接 | MCP 正式 + OTel + CacheBreakDetector + AnthropicProvider 拆分 + Structured Output + OpenAPI + ProviderPresets + Skill Remote + Span.addEvent() | ✅ 已完成 |
| v0.3.2 | Skill 进化 | 多文件 Bundle + Search Path + SkillManageTool（CRUD）+ 变更历史 + 参数替换 | 2-3 周 |
| v0.4.0 | 生产级 | 自我改进 + 多租户 + Memory 重设计 + Agent 状态快照 + SkillRegistry SPI + Skill 组合/ZIP/安全扫描 | 根据用户反馈 |
