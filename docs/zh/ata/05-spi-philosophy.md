# SPI 设计哲学——扩展性与极简主义的平衡

*从 20+ SPI 的组合数学到"每一个抽象都必须证明自己"*

---

Kairo 的第一版有 7 个 SPI 只存活到了 v0.3 就被砍掉。

每一个在设计阶段都有"充分的理由"。实际运行后发现，它们的共同特征是从未拥有过第二个实现。一个永远只有一个实现的接口不是抽象——是间接调用。

过早的抽象比过早的优化更危险——优化做错了，你得到的是丑陋但正确的代码；抽象做错了，你得到的是优雅但无法修改的架构。SPI 驱动的扩展性，说白了就是：框架提供契约，你提供实现。但每一层契约都有代价。

扩展性回答"何时抽象"。极简主义回答"何时不抽象"。Kairo 的 SPI 设计就建立在这两个问题的张力之上。

## 扩展性的三个层次

大多数框架谈"扩展性"的时候，其实只在谈一件事：你可以换一个模型。OpenAI 换成 Anthropic，Anthropic 换成 Gemini。这是最浅的一层——接口替换。

Kairo 的扩展性分三层，每层的工程挑战不一样。

### 第一层：替换（Replace）

替换是最直接的扩展模式：拔掉一个实现，插入另一个，系统行为改变但契约不变。

ModelProvider 是最典型的例子。接口只有三个方法：

```java
@Stable(since = "1.0.0")
public interface ModelProvider {
    Mono<ModelResponse> call(List<Msg> messages, ModelConfig config);
    Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config);
    String name();
}
```

`call` 做一次完整推理，`stream` 做流式推理，`name` 返回提供者名称。Anthropic 的实现需要处理 Claude 的 tool_use 块格式；OpenAI 的实现需要处理 function_calling 格式；通义千问的实现需要处理 DashScope 协议。这些差异全部封装在实现内部，框架看到的只是 `Mono<ModelResponse>`。

这个接口从 v0.1 就存在，到今天没有改过一个方法签名。它被标记为 `@Stable(since = "1.0.0")`——意思是在整个 1.x 生命周期内，签名不会变。你今天写的 `ModelProvider` 实现，三年后应该还能编译通过。

MemoryStore 是另一个替换级 SPI。接口定义了 `save`、`get`、`search`、`delete`、`list` 五个主要方法，加上基于 `MemoryQuery` 的结构化搜索和 `recent` 便捷查询。内存实现用 ConcurrentHashMap，开发阶段零依赖启动。Redis 实现用 Lettuce 异步客户端，生产环境高吞吐。JDBC 实现用 R2DBC 连接池，传统企业无缝接入。三种实现，三种持久化语义——但框架侧的代码完全不知道背后是什么存储。

ToolExecutor 同理。你可以替换整个工具执行引擎——换成沙箱执行、换成远程 RPC 执行、换成审批后执行。只要实现了契约，框架不问来源。

替换层的设计原则：一个 SPI 对应一个职责，运行时只有一个生效的实现。没有多实现的歧义，没有优先级的纠葛。

### 第二层：组合（Compose）

组合比替换复杂一个数量级。替换是"二选一"，组合是"有序链"——多个实现按优先级排列，依次执行，每个都可能修改或中止流程。

CompactionStrategy 是组合模式的代表。Kairo 的 6 阶段压缩引擎——Snip、Micro、Collapse、Auto、Partial、CircuitBreaker——每个阶段都是一个独立的 `CompactionStrategy` 实现：

```java
@Stable(since = "1.0.0")
public interface CompactionStrategy {
    boolean shouldTrigger(ContextState state);
    Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig config);
    int priority();
    String name();
}
```

`shouldTrigger` 根据上下文状态决定是否触发。`compact` 执行压缩。`priority` 决定执行顺序——数值越小越先执行。这意味着你可以在现有阶段之间插入新阶段（写一个 priority 为 150 的策略，它会在 Snip(100) 和 Micro(200) 之间执行），也可以替换某个阶段，或者在 CircuitBreaker 之后追加自定义的"极限压缩"。

六个阶段不是硬编码的。它们只是六个默认注册的 `CompactionStrategy` 实现。管线引擎不关心有多少个策略——它只是按 `priority()` 排序，依次问每个策略"要不要压缩"，收到肯定回答就执行。

GuardrailPolicy 是组合模式的另一个实例。安全护栏是链式的——多个策略按 `order()` 依次评估，任何一个返回 DENY 就短路整个链。一个企业的安全策略可能是这样组合的：

1. `PiiDetectionPolicy`（order=10）：检测并脱敏个人隐私信息
2. `InjectionDefensePolicy`（order=20）：检测 prompt 注入攻击
3. `CorporateCompliancePolicy`（order=30）：企业合规策略
4. `TokenBudgetPolicy`（order=40）：预算控制

这四个策略由不同团队编写，在不同时间部署，彼此完全不知道对方的存在。但它们通过 `order()` 值形成了确定性的执行顺序。新增一个策略不需要修改任何现有代码——只需要注册一个新的 Bean。

组合层的关键词：有序、可插入、可短路。

### 第三层：零侵入扩展

前两层仍然需要你写代码。第三层更进一步：你只需要加减 classpath 上的 JAR。

Kairo 的 13 个 Spring Boot Starter 是这个理念的物理实现。每个 Starter 都是一个独立的 Maven artifact，包含 Spring Boot AutoConfiguration。加到 `pom.xml` 里，能力自动激活。从 `pom.xml` 移除，能力完全消失——零残留。

这和"配置开关"不一样。配置开关意味着代码还在 classpath 上，只是被禁用了。这里是物理移除——类不存在，字节码不加载，内存不占用，攻击面不暴露。

"零侵入"意味着 kairo-core 的代码永远不会因为某个 Starter 的存在或缺失而改变行为。Core 通过 SPI 发现能力，有就用，没有就跳过。不存在"如果 classpath 上有 kairo-observability 就执行分支 A，否则执行分支 B"这种条件逻辑。

---

## 20+ SPI 全景图

Kairo 的全部 SPI 接口按功能域分类。这不是 API 文档，更像一张地图，让你看到整个扩展面的边界在哪里。

### 推理引擎层

| SPI | 职责 | 稳定性 |
|-----|------|--------|
| `ModelProvider` | 模型推理，支持流式 | @Stable |
| `ModelCatalog` | 模型名/别名解析，能力查询 | @Experimental |
| `ProviderPipeline` | 提供者管线（重试、降级、监控） | @Stable |
| `RoutingPolicy` | 成本感知的模型路由 | @Experimental |
| `CostTracker` | Token 用量与成本估算 | @Experimental |

`ModelProvider` 负责"调谁"，`ModelCatalog` 负责"叫什么"（别名解析），`RoutingPolicy` 负责"选谁"（基于成本、延迟、能力的动态路由），`CostTracker` 负责"花了多少"。四个接口各管各的，完全正交。

### 上下文管理层

| SPI | 职责 | 稳定性 |
|-----|------|--------|
| `ContextManager` | 上下文生命周期 | @Stable |
| `CompactionStrategy` | 压缩策略（6 阶段） | @Stable |
| `ContextSource` | 上下文注入源 | @Stable |
| `ContextBuilder` | 上下文构建 | @Stable |

上下文管理类似操作系统的虚拟内存子系统。`ContextManager` 管生命周期，`CompactionStrategy` 管回收算法，`ContextSource` 管数据来源，`ContextBuilder` 管组装逻辑。

### 治理与安全层

| SPI | 职责 | 稳定性 |
|-----|------|--------|
| `GuardrailPolicy` | 安全护栏（链式） | @Experimental |
| `HookChain` | Hook 生命周期调度 | @Experimental |
| `HookSessionContext` | 会话级 Hook 状态 | @Experimental |
| `PermissionGuard` | 工具权限门控 | @Stable |
| `ApprovalGate` | 人工审批门 | @Stable |

30 个 Hook 生命周期点（其中 10 个覆盖核心 Agent 循环）乘以 5 种决策值（CONTINUE / MODIFY / SKIP / ABORT / INJECT），构成了丰富的治理组合空间。

### 工具与能力层

| SPI | 职责 | 稳定性 |
|-----|------|--------|
| `ToolExecutor` | 工具执行框架 | @Stable |
| `ToolRegistry` | 工具注册表 | @Stable |
| `SkillStore` | 技能存储 | @Stable |
| `SkillRegistry` | 技能注册表 | @Stable |
| `PluginManager` | 插件生命周期 | @Experimental |

### 知识与记忆层

| SPI | 职责 | 稳定性 |
|-----|------|--------|
| `MemoryStore` | 持久知识存储 | @Stable |
| `EmbeddingProvider` | 向量嵌入 | @Stable |
| `SessionStorageProvider` | 会话持久化 | @Stable |

### 协调与通信层

| SPI | 职责 | 稳定性 |
|-----|------|--------|
| `TeamCoordinator` | 多 Agent 编排 | @Stable |
| `EvaluationStrategy` | 评估策略 | @Stable |
| `Channel` | IM 渠道适配器 | @Experimental |
| `Gateway` | 多渠道编排层 | @Experimental |
| `A2aClient` | Agent-to-Agent 协议 | @Experimental |

### 进化与诊断层

| SPI | 职责 | 稳定性 |
|-----|------|--------|
| `EvolutionPolicy` | 自进化策略 | @Experimental |
| `LspService` | LSP 诊断子系统 | @Experimental |
| `AcpAgent` | Agent Client Protocol | @Experimental |

## Starter 的组合数学

13 个 Starter，理论上有 2^13 = 8192 种组合。当然不是每种组合都有意义，但三个典型配置覆盖了 80% 的场景。

### 最小配置：裸奔 Agent

```xml
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-core</artifactId>
</dependency>
```

一个依赖。你得到：ReAct 循环引擎、6 阶段上下文压缩、Hook 生命周期、熔断器、循环检测。没有 MCP，没有观测，没有多 Agent——但这已经是一个完整的、可在生产环境运行的单 Agent 系统。

类比的话，相当于一台只装了内核的 Linux——没有图形界面，没有包管理器，但它能跑进程，能管内存，能处理中断。

### 典型配置：生产单 Agent

```xml
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-core</artifactId>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-mcp</artifactId>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-observability</artifactId>
</dependency>
```

三个依赖。加上 MCP 协议支持和 OpenTelemetry 观测。大多数生产系统的 Agent 都是这个配置。

### 全量配置：平台级 Agent

```xml
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-core</artifactId>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-mcp</artifactId>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-multi-agent</artifactId>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-evolution</artifactId>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-observability</artifactId>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-plugin</artifactId>
</dependency>
```

七个依赖。完整的平台级能力：多 Agent 协调、自进化管道、多渠道编排、插件生态。

关键一点：从最小配置到全量配置，kairo-core 的代码没有变化。不是"配置参数不同"——是"classpath 上的 JAR 不同"。Core 永远是那个 Core。能力的增减是物理级的。

## 第八原则：SPI Earned, Not Free

扩展性的三个层次回答了"如何抽象"。但更紧要的问题其实是：什么时候不抽象？

2012 年，Rich Hickey 在 Strange Loop 会议上做了 *Simple Made Easy* 的演讲。他区分了两个经常被混淆的概念：Simple（简单）和 Easy（容易）。Simple 是客观的——一个东西是否有纠缠的复杂性。Easy 是主观的——一个东西是否在你的能力范围内。软件行业的很多灾难来自追求 Easy 而牺牲 Simple。引入一个 ORM 框架让数据库操作 easy 了——但系统变得不再 simple。

每一层抽象都在用 simplicity 换 easiness。有时候这个交易值得做。但回头看，我在 Kairo 早期做的很多抽象，只是在给未来的自己制造债务。

Kairo 有八条设计原则。前七条定义了框架怎么做事——Skeleton-First、Embedded-First、SPI Extensibility、Reactive Inside / Pragmatic Outside、Progressive Adoption、Sensible Defaults、Fully Optional Extensions。第八条定义了框架不做什么。

在深入第八原则之前，值得展开第四原则——Reactive Inside / Pragmatic Outside——因为它不是一个技术偏好，而是 Agent 运行时的结构性需求。

Kairo 的每一个 SPI 方法都返回 Project Reactor 的 `Mono<T>` 或 `Flux<T>`。`Agent.call()` 返回 `Mono<Msg>`，`ModelProvider.stream()` 返回 `Flux<ModelResponse>`，`ToolExecutor.execute()` 返回 `Mono<ToolResult>`。这不是赶时髦——Agent 运行时面对的几个结构性问题，都指向 Reactive 作为比较合理的解法：

非阻塞 I/O。模型调用和工具执行本质上是 I/O 密集型操作——一次 Claude API 调用可能耗时 5-30 秒。在阻塞模型下，一个线程在等待 HTTP 响应的全程被占用。当 10 个 Agent 共享一个线程池时，一个阻塞的模型调用就能饿死其他 9 个。Reactive 的非阻塞 I/O 让线程在等待期间可以服务其他 Agent。

协作式取消。用户按下 Ctrl-C，或者 `CancellationSignal` 触发。在阻塞模型下，你只能等当前阻塞调用返回——也许 10 秒后。在 Reactive 模型下，取消信号通过 Reactor Context 传播，正在进行的 HTTP 请求被立即取消。对于一个可能运行 100+ 轮的 Agent 来说，"立即响应取消"不是可有可无的体验优化。

背压。流式模型响应天然支持背压机制。如果终端渲染器跟不上 token 输出速度，Reactive 流会自动减速，而不是把 token 堆积在无界缓冲区里直到内存溢出。

栈安全的递归。ReAct 循环本质上是递归——reason、act、observe、再 reason。在传统递归中，100 轮迭代意味着 100 层调用栈。Kairo 的 ReAct 循环使用 `Mono.defer()` 实现栈安全的递归迭代——每一轮都在同一个栈帧上执行，不会因为长会话而栈溢出。

上下文传播。Agent 特有的状态——`ToolContext`、诊断信息、租户 ID——通过 Reactor 的 `contextWrite()` 传播，而不是 ThreadLocal。当多个 Agent 共享同一个 `ToolExecutor` 实例时，ThreadLocal 会互相污染；Reactor Context 天然隔离。

不过"Pragmatic Outside"同样重要。框架内部全面 Reactive 不意味着用户必须成为 Reactor 专家。Kairo 为每个主要 SPI 提供了 `callBlocking()` 便捷方法——简单场景下，你不需要理解 `Mono` 和 `Flux` 就能使用框架。内部的复杂性不应该泄漏到用户的 API 体验中。框架承担 Reactive 的复杂性，用户享受 Reactive 的收益——边界就划在这里。

回到第八原则：

> **Principle 8: SPI Earned, Not Free**
> 每一个新的 SPI 都必须证明自己存在的必要性。抽象不是免费的——它是一张信用卡，你在用未来的灵活性为今天的整洁买单。

我为它设定了四个准入条件，任何新 SPI 必须至少满足其中三个：

1. 接口方法大于 5 个。如果一个接口只有两三个方法，它很可能不需要作为独立 SPI 存在。一个只有 `execute(Input): Output` 的接口，本质上就是一个 `Function<Input, Output>`——Java 标准库已经有了。

2. 存在多种实现策略。如果一个接口永远只有一个实现，它不是抽象——它是间接调用。只有当你能列举出至少两种截然不同的实现策略时（不是参数变化，是算法差异），这个接口才值得提取。

3. 需要组合优于继承。SPI 的价值在于组合——让不同的实现可以在运行时被替换和组装。如果你不需要这种运行时灵活性，一个 `abstract class` 就够了。

4. 扩展点需要与实现解耦。如果扩展逻辑和核心逻辑总是一起改，那么拆到两个接口只是在制造仪式感（ceremony），没有真正解耦。

### 被拒绝的抽象

理解一个设计的取舍，看它拒绝了什么往往比看它做了什么更有信息量。

**ContextAwareToolHandler。** 让工具在执行时感知当前对话上下文——知道之前调用了哪些工具，用户问了什么问题。听起来合理，但被否决了。原因：现有的 `ToolContext` 依赖注入已经覆盖了这个需求。引入新接口只会创造两种做同一件事的方式——Rich Hickey 称之为 complection。每个新开发者都会问："我应该用哪个？"这个问题本身就不应该存在。

**QueryableMemoryStore。** 在 `MemoryStore` 之上创建支持结构化查询的子接口——按时间范围查询、按关键词搜索、按元数据过滤。听起来有用，但被否决了。`MemoryStore` 已经有 `search` 方法。更复杂的查询需求应该在具体实现中处理——Elasticsearch 实现天然支持全文搜索，pgvector 实现天然支持向量相似度查询。把查询能力抽象到 SPI 层，是一个经典的 Leaky Abstraction 陷阱——Joel Spolsky 在 2002 年就写过这个问题。

**WorkflowEngine。** 创建显式的工作流引擎 SPI——定义步骤、转移条件、并行分支。听起来很 enterprise，但被否决了。原因有二：A2A 协议已经提供了多 Agent 间的编排能力；更根本的是，Agent 的本质是自主决策。一个被工作流引擎约束的 Agent 就不再是 Agent——它是一个脚本执行器。如果你确定知道每一步该做什么，你需要的是 Temporal 或 Apache Airflow，不是 Agent 框架。

每一个被否决的提议都被记录在架构决策记录中，包括否决的理由和"如果未来情况变化应该重新评估"的触发条件。否决不是永久的——但默认应该是否决。

## 30 个 ADR 精选

Kairo 有 30 个 ADR（Architecture Decision Record）。代码告诉你系统"是"什么，但不告诉你"为什么"是这样。六个月后，一个新团队成员看到 `CompactionEngine` 有六个阶段，想花两周"简化"它——如果没有 ADR-006 用三段话阻止这种浪费。

### ADR-001: ReAct 循环分解

**背景**：`ReActLoop` 类膨胀到 1,008 行，同时承担迭代控制、模型调用、工具执行和 Hook 路由四个职责。

**决策**：分解为四个聚焦的阶段组件，`ReActLoop` 保留为 263 行的薄编排器：

```text
ReActLoop（编排器 ~263 LOC）
  ├── IterationGuards    — 迭代限制、token 预算、协作取消
  ├── ReasoningPhase     — 模型调用、响应解析
  ├── ToolPhase          — 工具分发、并行执行、超时
  └── HookDecisionApplier — Hook 求值、流程控制
```

**后果**：每个阶段独立可测。分解后，原有的 1,211 个测试全部通过，零修改——证明了行为等价性。这算是一个值得做的抽象——它满足了四个准入条件中的每一个。

### ADR-006: 六阶段压缩管线

**背景**：Agent 会话中的上下文无限增长。初始方案是截断旧消息——太粗暴，重要的早期上下文丢失。

**决策**：实现六阶段分层压缩管线，基于压力触发。

| 阶段 | 触发压力 | LLM 参与 | 操作 |
|------|---------|---------|------|
| Snip | 80% | 否 | 删除未引用的工具调用/结果对 |
| Micro | 85% | 轻量 | 将工具结果摘要为一行 |
| Collapse | 90% | 否 | 合并连续同角色消息 |
| Auto | 95% | 中等 | 滑动窗口 LLM 摘要 |
| Partial | 98% | 重度 | 除最近 N 条外全部摘要 |
| CircuitBreaker | 99% | 紧急 | 整个对话压缩为单条消息 |

**证据链**：这个设计有数据支撑。Morph FlashCompact 的研究验证了多阶段压缩相比单次压缩的优势——37% vs 98% 的信息损失率。好的抽象应该有证据，不只是直觉。

### ADR-012: ResourceConstraint SPI

**背景**：迭代守卫（maxIterations、tokenBudget、timeout）硬编码在 `IterationGuards` 和 `DefaultReActAgent` 中。自定义约束——成本上限、外部终止信号、合规限制——无法插入。

**决策**：引入 `ResourceConstraint` SPI，四级响应：`ALLOW` → `WARN_CONTINUE` → `GRACEFUL_EXIT` → `EMERGENCY_STOP`。原有的三个硬编码检查被重构为三个内置约束实现，现有配置完全向后兼容。

这个例子说明了一件事：SPI 的正确引入时机不是设计初期"以防万一"，而是硬编码方案已经被证明不够用的时候。三个硬编码检查跑了五个版本没出问题。当第四种需求（成本约束）出现时，我才动手引入 SPI。如果在 v0.1 就做这个抽象，大概率会做错。

### ADR-029: Plugin 格式兼容性

**背景**：Kairo 到 v1.1 没有插件系统。同时，Claude Code 已经拥有工业级的插件生态——35+ 官方插件、五种安装源、声明式文件格式。

**决策**：读 Claude Code 的文件格式，但使用 Kairo 的运行时基础设施。五个不可协商项：格式兼容但目录不兼容（`.kairo-plugin/`）；Plugin SPI 与现有 SPI 并存绝不替代；组件注册原子性；变量名兼容（`${KAIRO_PLUGIN_ROOT}` canonical，`${CLAUDE_PLUGIN_ROOT}` compat alias）；Marketplace = git，不是 Kairo 托管的服务器。

这个决策的思路是：在格式层面拥抱兼容性，在语义层面保持独立性。读别人的文件格式是务实的选择——Claude Code 的格式已经经过了大量用户验证。但你不需要复刻别人的运行时。

---

## 机械契约

有扩展性而没有兼容性，等于给用户挖坑——用户今天写的实现，明天框架一升级就编译不过了。

Kairo 用三个注解来声明 API 的稳定性契约。这不只是文档标签——它们通过 `japicmp-maven-plugin` 在每次构建时强制执行二进制兼容性检查。

### @Stable：不可打破的承诺

```java
@Stable(value = "Core model invocation contract; unchanged since v0.1", since = "1.0.0")
public interface ModelProvider { ... }
```

被 `@Stable` 标记的接口，在整个主版本周期内（1.x）签名不变、语义不变、存在性不变：

- 不能删除、重命名或改变签名
- 可以添加带默认实现的新方法
- `@Stable` 元素永远不能降级为 `@Experimental` 或 `@Internal`
- 要移除一个 `@Stable` 元素，必须先在 vN.x 标记 `@Deprecated(forRemoval=true)`，然后在 v(N+1).0 才能删除

### @Experimental：标注了风险的试探

```java
@Experimental("Guardrail SPI — contract may change before v1.2.0 stabilization")
public interface GuardrailPolicy { ... }
```

签名可能在小版本之间变化。每个 `@Experimental` 注解都附带稳定化的预期时间线——这是一个工程承诺：我知道这个接口还不够成熟，但承诺在特定版本之前做出最终决定。

### @Internal：红线

```java
@Internal("Referenced by @Stable surface, cannot relocate without breaking it")
public interface SomeInternalType { ... }
```

因为技术原因存在于 `kairo-api` 包中，但不是公共契约。任何版本都可能改变它。

三级注解形成了一个单向的渐进稳定化路径：`@Experimental` → `@Stable` → 永远不倒退。v1.1 的类型分布：141 个 `@Stable` 类型，147 个 `@Experimental` 类型。

### japicmp：让机器执法

这些规则不靠文档和 code review 来保证。每次构建，japicmp 自动比对当前版本与上一个发布版本的二进制兼容性。任何对 `@Stable` 接口的破坏性变更，构建直接失败。

靠人来遵守兼容性承诺，迟早会出问题——赶 deadline 的时候，凌晨三点的时候。机器不会。构建系统不疲劳，不侥幸，这就是把规则交给机器执行的意义。

## 主动拒绝清单

一个框架的品味体现在它选择不做什么。以下是 Kairo 明确拒绝构建的七项能力，以及拒绝的理由。

1. **Hybrid Search 实现。** pgvector 已经商品化了 80% 的向量搜索场景。在 Agent 框架层重新实现混合检索，是在和有二十年优化积累的数据库生态竞争。

2. **ML-based 模型路由。** AWS Bedrock 正在集成 Intelligent Routing，Azure OpenAI 的 Model Router 也在路上。这是云平台的自然延伸——框架层做这个，等于用二手信息和一手信息竞争。

3. **Eval 框架。** 评估 Agent 质量是一个独立的产品类别。Langfuse、Arize、LangSmith 各有数千万融资。在 Agent 框架里嵌入 eval 系统，就像在 Web 框架里嵌入 APM——能做，但做不过专门工具。

4. **多 Agent Debate。** 让多个 Agent 辩论来提高决策质量——学术论文上很漂亮。但我们试过，实际效果不如预期：一个强模型加好的工具，往往胜过多个弱模型的辩论。三个 Agent 辩论三轮，token 消耗是单 Agent 的九倍，质量提升有限。

5. **Studio / Dashboard UI。** Dify、Langflow、Copilot Studio——市场上已经有太多"拖拽构建 Agent"的工具了。Kairo 是运行时基础设施，不是低代码平台。

6. **Docker/K8s Sandbox。** 容器编排是运维关注点，不是运行时关注点。Kairo 提供 `ExecutionSandbox` SPI（ADR-025），定义了沙箱的抽象契约。具体实现交给运维团队——他们对容器的理解比我们深得多。

7. **Skill Marketplace。** 运行时还没有完全稳定，过早引入 marketplace 会把框架的发展速度绑定在生态兼容性上。

这些拒绝背后有一个共同的判断：这件事是否会被 LLM 的能力提升所商品化，或者被云平台所吸收？如果答案是"很可能"，投入研发资源就是浪费。

框架应该把精力花在不容易被商品化的地方——状态管理、安全护栏、上下文压缩、错误恢复、成本管控、human-in-the-loop。这些是结构性问题。模型再聪明，也需要有人管理它的上下文窗口、限制它的执行权限、在它失败时重试。

## 业界对比

扩展性不是一个布尔值。说"X 框架支持扩展"没什么信息量——所有框架都"支持扩展"。具体要看：扩展什么，扩展到什么程度，扩展的边界在哪里。

### Claude Code：能力内置，不可替换

Claude Code 是很好的产品，但它的扩展性模型和 Kairo 根本不同。它的 5 层压缩管线是硬编码的；43 个工具实现是内置的；模型绑定 Anthropic。这不是缺陷——Claude Code 做了一个合理的产品决策：为 Anthropic 用户提供最佳体验。代价是它是一个产品，不是一个框架。产品解决具体问题；框架让你解决自己的问题。

### LangChain4j / Spring AI：API 层扩展

LangChain4j 和 Spring AI 提供了不错的 API 层扩展性——你可以换模型，换向量存储，换 embedding 提供者。但你不能替换上下文管理策略、插入安全护栏到执行管线中、替换循环检测算法——因为它们没有这些能力。这是"API 封装层"与"运行时"的区别。API 封装层帮你调模型；运行时帮你运行 Agent。调模型只是运行 Agent 的一小部分。

### LangChain：抽象层数的反面教材

LangChain 提供了一切——每种模型、每种工具、每种记忆都有对应的抽象。但抽象层数多到"如何正确使用 LangChain"本身成了一个需要学习的学科。一个新手想理解一个简单的 RAG 管线如何工作，需要穿过 `Chain` → `Runnable` → `RunnableSequence` → `BaseRetriever` → `VectorStoreRetriever` → `Embeddings` → `VectorStore` 七层抽象。

Kairo 的做法是：少一些抽象层数，多一些每层的承载力。基础接口只有 5 个：`Agent`、`ModelProvider`、`ToolExecutor`、`ContextManager`、`MemoryStore`。从这五个接口出发，你可以构建一个完整的 Agent 应用。其余的 SPI 是你在需要的时候才去了解的。

| 扩展维度 | Claude Code | LangChain4j | Spring AI | Kairo |
|----------|------------|-------------|-----------|-------|
| 模型替换 | 不支持 | 支持 | 支持 | 支持 |
| 存储替换 | 不支持 | 支持 | 支持 | 支持 |
| 压缩策略替换 | 不支持 | 无此能力 | 无此能力 | 支持 |
| 安全策略链 | 有限 | 无此能力 | 无此能力 | 支持 |
| 编排逻辑替换 | 不支持 | 无此能力 | 无此能力 | 支持 |
| 进化策略替换 | 无此能力 | 无此能力 | 无此能力 | 支持 |
| 渠道适配 | 不支持 | 无此能力 | 无此能力 | 支持 |
| 物理级能力增减 | 不支持 | 部分 | 部分 | 支持 |

框架的扩展性不是让你做更多的事，而是让你能替换它做事的方式。

---

## SPI 的代价

### SPI-everything 的成本

SPI-everything 不是没有代价。

学习曲线。20+ 个 SPI 接口意味着 20+ 套契约需要理解。我们的缓解方式是：每个 SPI 都有 `Noop` 默认实现，你可以从"什么都不做"开始，逐步替换。但这并不能完全消除新手面对这么多接口时的困惑。

模块数量。31 个 Maven 模块是一个真实的管理负担。用 `kairo-bom` 统一管理版本，用 cohort aggregator 批量构建——但模块间的依赖图仍然需要人来维护。有时候我也会想是不是拆得太细了。

间接性。当一个 `CompactionStrategy` 的行为不符合预期，你不能直接在 Core 里打断点——因为 Core 只是调用接口。间接性是所有 SPI 架构的固有税，没有办法完全消除。

### 极简主义的风险

另一面的代价同样真实。

API 表面积膨胀。Middleware SPI 是我做过的一个失误。设计了完整的 `Middleware` 接口和 `MiddlewarePipeline`——但它从未获得真正的采用。原因是 Hook 系统覆盖了 Middleware 试图解决的几乎所有用例。结果是用户看到了两种做拦截的方式——"我应该用哪个？"回头看，如果当初更果断地做决定，API 表面会干净得多。

God Object。当你拒绝创建新接口时，现有接口会承受越来越多的职责。`Agent` 接口在 v0.5 时有 5 个方法，到 v0.9 膨胀到了 11 个。我在 v1.0 做了手术——把 4 个方法拆到了 `AgentLifecycle` 和 `AgentSnapshot`。这次手术其实该更早做。那段时间每加一个方法都觉得"只多一个"，回头一数才发现问题有多严重。

Leaky Abstraction。`MemoryStore` 在某个版本被用来存储 session 状态——这不是它设计的目的。结果是 `InMemoryMemoryStore` 的实现越来越复杂，塞进了很多不属于它的逻辑。后来引入 `SessionStorageProvider` 才解决了这个污染。

### 平衡线

我给自己划了一条线：SPI 用于策略，硬编码用于机制。怎么做推理（策略）是可替换的；做不做推理（机制）是硬编码的。怎么压缩（策略）是可替换的；要不要压缩（机制）是引擎的内置行为。

这条线不总是清晰的。有时候我把应该硬编码的东西做成了 SPI（然后在下个版本简化掉），有时候把应该可替换的东西硬编码了（然后在用户反馈后补上 SPI）。线的存在比线的精确位置更重要——至少你知道应该往哪个方向修正。

一个实用的启发式规则：当你发现自己在给一个接口的文档写"注意：这个方法在 X 场景下有不同的语义"时，大概需要一个新接口了。

## Worse is Better

三十年前，Richard Gabriel 写了一篇文章叫《Worse is Better》。他对比了两种设计哲学——MIT/Stanford 风格（追求正确性和完整性，即使牺牲简单性）和 New Jersey 风格（追求简单性，即使牺牲某些正确性和完整性）。

Unix 之所以赢了，不是因为它更好，而是因为它更简单——简单到足够正确。Unix 的文件系统是一个字节流，不是一个记录结构。Unix 的进程模型是 fork/exec，不是一套精密的进程代数。

简单的东西容易理解，容易理解的东西容易实现，容易实现的东西容易移植，容易移植的东西容易传播。HTTP 比 CORBA 简单——HTTP 赢了。JSON 比 XML 简单——JSON 赢了。REST 比 SOAP 简单——REST 赢了。这条链条解释了很多违反直觉的技术胜利。

Rich Hickey 在演讲的最后说了一句话：

> "Simplicity is a prerequisite for reliability."

简单是可靠的前提。一个你不理解的系统，你无法让它可靠。一个有七层抽象的系统，没有人能完全理解——包括它的设计者。

Kairo 的 30 个 ADR、四个准入条件、七项拒绝构建的能力、三级稳定性注解、japicmp 机械门禁——这些工具服务于同一个目的：让系统保持足够简单，以至于它可以是可靠的。

"足够简单"和"最简单"之间有很大的空间。这中间的分寸感，大概就是做框架最难的部分。Kairo 不是最强大的 Agent 框架——但它试图做到的是：足够简单，足够可靠。这个目标每天都在被 30 个 ADR 检验。

有一个问题我每次想加新 SPI 的时候都会问自己：这个抽象，真的挣到了自己的位置吗？如果答案不是很确定的"是"，我就先不加。

*下一篇：《Hook——Agent 的治理层》*

---

**参考**

1. Rich Hickey, "Simple Made Easy," Strange Loop, October 2011
2. Richard P. Gabriel, "Worse is Better," 1989 (essay), revised 1991
3. Joel Spolsky, "The Law of Leaky Abstractions," Joel on Software, November 2002
4. Morph FlashCompact, "Multi-stage Context Compression for LLM Agents," 2025
5. Michael Nygard, "Documenting Architecture Decisions," Cognitect Blog, November 2011
6. Kairo API Module, `io.kairo.api.*`, SPI interface definitions, 2025-2026
7. Kairo ADR-001 through ADR-030, Architecture Decision Records, 2025-2026
8. Spring Boot AutoConfiguration Reference, Spring Framework 6.x / Boot 3.3
9. japicmp — Java API Compatibility Checker, https://github.com/siom79/japicmp
10. VILA-Lab, "Dive into Claude Code: The Design Space of Today's and Future AI Agent Systems," arXiv:2604.14228, April 2026
