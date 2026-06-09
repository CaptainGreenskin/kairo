# 前沿：分布式、自进化与 Plugin 生态

*系列终章——当 Agent 跨越机器边界、学会自我进化*

所有"多 Agent"方案都有一个秘密：它们都跑在同一台机器上。

Claude Code 的 swarm 模式——Leader Agent 启动多个 Worker，分头修改代码、运行测试。看起来像一个分布式系统？实际上，Worker 之间通过 `~/.claude/work/ipc/` 目录中的磁盘文件传递消息，500 毫秒一次轮询。这是文件系统 IPC。所有进程共享同一个文件系统，同一块内存，同一台物理机。

Kairo 的 A2A 实现也是如此。`TeamCoordinator` 编排多个 Agent，通过 `MessageBus` 传递消息。但 `MessageBus` 的默认实现是 Reactor `Sinks.Many`——一个 JVM 进程内的响应式发布器。所有 Agent 运行在同一个 JVM 中，共享同一个堆内存。

这不是真正的分布式。

当一个重构任务需要三个 Agent 同时满载运行——每个都需要完整的上下文窗口（200K+ token）、频繁的模型调用、独立的工具执行环境——一台机器扛不住。

"多 Agent"和"分布式 Agent"之间的距离，不是一条网线。是一个迥异的工程问题。

这篇文章讨论三件事。第一，分布式 Agent 为什么比分布式微服务更难，以及一个可能的架构方向。第二，Agent 如何从经验中学习——自我进化的机制与治理。第三，Plugin 生态如何让 Agent 站在社区的肩膀上。这是本系列的最后一篇。

## Part 1：分布式 Agent

### 比微服务更难的四个原因

十年的微服务经验给了我丰富的基础设施——服务发现、负载均衡、熔断降级、分布式追踪。直觉上，分布式 Agent 似乎可以复用这些。Agent 就是一个服务嘛——给它一个 HTTP 端点，用 Kubernetes 部署。

这个直觉是错的。分布式 Agent 和分布式微服务之间有四个根本性差异。

**状态的本质不同。** 微服务的根本设计原则是无状态服务 + 有状态存储。状态全部外置到数据库，一个实例崩溃了，换一个新实例继续处理。但 Agent 的"状态"是上下文窗口——一个有时间因果性的对话历史：第 3 步的工具调用结果影响第 5 步的推理方向，第 5 步的决策约束第 8 步的代码生成。其中有压缩标记、有 verbatim 锁定、有工具调用的因果链。这更像数据库的 WAL 而不是键值存储。你不能把它"存到 Redis"——因为模型的推理就建立在完整对话历史之上。

**协调成本是 token。** 微服务之间传递几百字节的 JSON，延迟几毫秒，协调成本几乎为零。Agent 之间的协调截然不同。Agent A 修改了 `UserService.java` 的方法签名，需要通知 Agent B。这个通知必须包含足够的上下文让 B 理解变更的含义——这些理解，全部以 token 计价。更要命的是，微服务的协调成本是 O(1)——每次 API 调用大小大致恒定；Agent 的协调成本是 O(n)——随着状态分歧线性增长。

**故障域截然不同。** 微服务崩溃后，重启就好。Agent 崩溃意味着上下文窗口丢失——40 步交互中逐步构建的理解、20 次文件读取的紧要信息、10 次代码修改的决策推理。恢复不是简单的"从检查点重放"，你需要恢复上下文、评估副作用、决定恢复点、重新注入理解。跨机器时更复杂：Agent 在机器 A 崩溃，部分工作成果还在 A 的文件系统上，检查点可能在机器 B 的持久化存储中。

**工具访问有拓扑约束。** 微服务通过网络 API 暴露能力，只要网络可达就能调用。偏偏 Agent 的基础工具——文件读写、Shell 执行、git 操作——本质上是本地操作。每个 Agent 的工具集都与其运行环境的物理拓扑绑定。归根结底是网络拓扑问题，不是权限问题。

### 架构方向：无状态协调 + 可恢复 Agent

面对这四个根本困难，分布式 Agent 需要自己的架构原则。

```
┌──────────────────────────────────────────────────────────┐
│                 Coordinator Service                       │
│            (stateless, Plan + Dispatch)                   │
└──────┬──────────────┬──────────────────┬─────────────────┘
       │ A2A/HTTP     │ A2A/HTTP         │ A2A/HTTP
       ▼              ▼                  ▼
┌─────────────┐ ┌─────────────┐  ┌─────────────┐
│  Agent 1    │ │  Agent 2    │  │  Agent 3    │
│  (code)     │ │  (test)     │  │  (review)   │
│ Local tools │ │ Local tools │  │ Local tools │
│ Worktree A  │ │ Worktree B  │  │ Worktree C  │
│ Machine 1   │ │ Machine 2   │  │ Machine 3   │
└─────────────┘ └─────────────┘  └─────────────┘
       │              │                  │
       └──────────────┴──────────────────┘
                      │
                 Git Remote
              (coordination hub)
```

四个根本原则：

**Coordinator 无状态，Agent 有状态但可恢复。** Coordinator 只做 Plan 分解、Task 分派、结果收集和冲突仲裁，可随时重启。Agent 节点通过 `DurableExecutionStore` 实现可恢复——`appendEvent` 以增量方式记录重要事件，`recover` 从持久化存储重建执行历史，`expectedVersion` 提供乐观锁防止并发恢复冲突。分布式场景下，实现从本地文件切换到 PostgreSQL。SPI 不变，实现变。

**通信通过 EventBus，不用 RPC。** Agent 每一步操作可能花费数十秒到几分钟，同步调用会阻塞调用方并撑大上下文。Kairo 的 `MessageBus` SPI 定义了异步通信。当前默认实现是 Reactor Sink；分布式场景下换成 Redis Streams 或 Kafka。`kairo-event-stream` 模块已经体现了这个设计——transport-agnostic 的事件总线，有 SSE 和 WebSocket 两种传输实现。传输变了，语义不变。

**Worktree 是进程隔离的单元。** 微服务用 Docker 做进程隔离，分布式 Agent 的"容器"是 git worktree。`git worktree` 允许同一仓库下创建多个独立工作目录，每个绑定独立分支。它天然提供了并行修改、冲突检测、原子提交、历史审计和内置回滚。在分布式场景中，每台机器上的 Agent 拥有自己的 worktree 或独立 clone，完成后 push 到远程，Coordinator 执行 merge。

**结果聚合靠 Evaluate Agent，不靠文本合并。** 代码合并没有冲突，不代表语义一致——Agent A 修改了方法签名，Agent B 基于旧签名写测试，git merge 不会报冲突，但合并后的代码无法编译。Kairo 的 `EvaluationStrategy` SPI 让 Evaluate Agent 执行编译检查、测试验证、接口一致性和语义冲突检测。

### Git 作为协调协议

在分布式 Agent 架构的每一个要害问题上，git 都提供了现成的解决方案。这不是巧合——git 本身就是为"多个人并行修改同一个代码库"而设计的。

| 分布式 Agent 需求 | git 提供的能力 |
|---|---|
| 并行工作，互不干扰 | 分支（branch） |
| 独立的工作空间 | worktree / clone |
| 冲突检测 | merge conflict detection |
| 原子提交 | commit |
| 变更审计 | git log |
| 增量同步 | git fetch / pull / push |
| 离线工作 | 分布式架构（每个节点有完整历史） |
| 变更审查 | pull request / merge request |

最重要的是：git 是代码 Agent 已经在使用的工具。用已有工具的能力做协调，不引入额外基础设施——这是复杂系统设计中最珍贵的性质。

一个具体的协调流程：假设 Coordinator 把"添加 JWT 认证"任务分给三个 Agent。Step 1：创建三个分支——`feature/jwt-service`、`feature/jwt-test`、`feature/jwt-review`。Step 2：每个 Agent 在自己的 worktree 中工作——Agent 1 创建 JwtTokenProvider 和 SecurityConfig，Agent 2 编写测试，Agent 3 检查安全规范。Step 3：各自 push 到远程。Step 4：Coordinator 触发 Evaluate Agent，拉取三个分支，尝试 merge，发现 Agent 2 的测试基于旧接口，通知 Agent 2 更新（附带 Agent 1 的变更摘要），修复后重新 push。Step 5：Evaluate Agent 验证合并后代码——编译通过、测试通过、安全审查通过，合并到 main。

整个过程中，Agent 间的"通信"不是直接的消息传递——而是通过 git 的分支和 merge 间接协调。每个 Agent 只需要关注自己的分支。这是一种最终一致性模型：每个 Agent 在本地独立工作（强一致的本地状态），通过 git push/merge 实现全局协调。

amux 项目已经验证了这个方向——在一台机器上启动多个 Agent 进程，每个工作在独立的 git worktree 中，通过 git merge 合并结果。虽然仍运行在单机上，但它的协调机制天然可分布。只是 amux 的编排是外部脚本驱动的，缺少框架级的 SPI 抽象，没有检查点恢复、没有语义级冲突检测、没有 Evaluate Agent 的概念。它是优秀的概念验证，不是完整的解决方案。

### Kairo 的分布式接缝

Kairo 今天还不是分布式 Agent 框架。但它的架构中已经预留了决定性的接缝——可被替换为分布式实现的 SPI 边界：

- `MessageBus` → Reactor Sink（单机）→ Redis Streams（分布式）
- `DurableExecutionStore` → 本地文件（单机）→ PostgreSQL（分布式）
- `WorkspaceProvider` → 本地目录（单机）→ Git Clone（分布式）
- `kairo-event-stream` → 进程内（单机）→ SSE/WebSocket/Kafka（分布式）

每一个替换都是 SPI 实现的变更，不需要修改 Agent 的业务逻辑。接缝不增加今天的复杂度，但为明天的扩展保留了空间。

这些接缝的共同特征是：SPI 接口不变，只是实现层的替换。这正是 SPI 抽象的设计意图——让系统的行为可以在不修改调用方的前提下发生质变。

| 方案 | 跨机器 | 检查点恢复 | 冲突检测 | 语义聚合 | 基础设施依赖 |
|---|---|---|---|---|---|
| Claude Code Swarm | 否 | 有限 | 无 | 无 | 无 |
| amux + Git Worktree | 可扩展 | 无 | Git 内置 | 无 | Git |
| Temporal + LLM | 是 | 完整 | 无 | 无 | 重（Temporal Server） |
| LangGraph Cloud | 是 | 有 | 无 | 无 | 中（LangGraph Server） |
| Kairo（设计方向） | 可扩展 | SPI 已有 | Git 内置 | EvaluationStrategy | 轻（SPI 按需装配） |

诚实地说，分布式 Agent 的几个根本性难题——检查点与文件系统状态的一致性、上下文分歧的不可逆性、token 成本的超线性增长——我还没有完整答案。3 个 Agent 独立工作各消耗 100K token，但加上协调开销（Coordinator Plan 生成、状态同步、Evaluate Agent 检查），总成本约 400K——比单 Agent 完成同一任务多一倍。分布式带来的并行加速必须超过协调开销，才有正的 ROI。

不过微服务时代的教训很清楚：**分布式不是目标，是手段。** 先把单机做好，再考虑跨机器。只是为跨机器预留接缝，从第一天就应该开始。

---

## Part 2：Agent 会进化

### 玄武实验室的意外发现

2026 年 1 月，腾讯玄武实验室在内部报告中披露了一个意外发现。

他们的 Hermes Agent 在经历了数月的对抗性安全测试后，开始自主创建安全审查 Skill。没有人在 prompt 中要求它这么做。它在数百次对话中反复遇到类似的攻击模式——SQL 注入伪装成用户输入、提示注入藏在 Markdown 注释中、恶意依赖伪装成合法包——然后它自己学会了：每次遇到新代码时，先运行一套它自己总结出来的安全审查步骤。

激动人心——Agent 可以自主扩展能力。令人不安——如果 Agent 可以自己创建能力，谁来治理这些能力？

一个自主创建的 Skill，可能是高效的安全审查流程，也可能是绕过安全检查的后门。没有治理管线，你无法区分两者。

自我进化的根本矛盾浮出水面：**你需要 Agent 变得更聪明，但你不能让它在不受监督的情况下变聪明。**

### 三阶段治理管线

Kairo 的进化管线——`kairo-evolution` 模块——用三个阶段回答这个矛盾：**QUARANTINE（隔离）→ SCAN（扫描）→ ACTIVATE（激活）**。

**隔离。** 当 `EvolutionPolicy` 从对话历史中提取出候选 Skill 时，它不会直接进入运行时，而是被标记为 `DRAFT` 信任等级，存入 `EvolvedSkillStore`。`SkillTrustLevel` 有三个级别：`DRAFT(0)` → `VALIDATED(1)` → `TRUSTED(2)`。只有 `VALIDATED` 及以上的 Skill 才会被注入 Agent 的 system prompt。一个 `DRAFT` 的 Skill 存在于存储里，但对 Agent 来说是不可见的。即使提取逻辑出了错，产生了有害的 Skill，它也不会影响 Agent 的行为。

**扫描。** 管线对候选 Skill 进行内容扫描——检查基本完整性（非空、合理长度）和已知注入模式（`ignore previous instructions`、`you are now` 等）。这是一个保守的、零延迟、零成本、零外部依赖的规则检查。模型驱动的安全扫描可以在后续版本中作为额外的 `ScanPolicy` SPI 插入。

**激活。** 扫描通过后，Skill 的信任等级提升到 `VALIDATED`，正式进入运行时。扫描失败则从存储中删除。整个管线是原子的：要么 Skill 通过所有检查并激活，要么它从未存在过。

背后的状态机还定义了一个致命状态：**SUSPENDED**。当进化管线连续失败达到阈值时，所有新的进化提交被静默跳过，等待人工干预。这解决了"进化-失败-进化"的死循环，也承认了一个事实：进化不是一个永远正确的过程。当系统检测到自己的进化能力出了问题时，最理性的行为不是继续尝试，而是停下来。

进化子系统暴露三个正交的 SPI——`EvolutionPolicy`（审查什么）、`EvolvedSkillStore`（存到哪里）、`EvolutionTrigger`（什么时候触发）——每个维度独立变化，组合空间是乘法而不是加法。策略是可替换的：安全公司可以实现只关注安全模式的 `SecurityFocusedEvolutionPolicy`，法律科技公司可以实现只在合同审查中触发的 `LegalPatternEvolutionPolicy`。框架不关心策略内容——它提供管线和治理；你提供审查逻辑。

进化不只从成功中提取模式——它也从失败中学习。`FailurePatternTracker` 追踪跨会话的失败签名，每个失败被归纳为一个三元组：`(errorType, toolName, messagePrefix)`。当同一签名在 30 天内出现 3 次以上，追踪器触发，返回累积的失败模式供进化审查使用。这个设计的精髓在于它的被动性——系统不主动寻找失败，让时间和频率来决定哪些失败值得关注。只有结构性失败——反复出现、暗示系统性问题的失败——才会被进化管线处理。

进化出来的 Skill 也不是永久存在的。`SkillQualityScorer` 为每个 Skill 计算质量分数：`Score = usageWeight * normalizedUsage + recencyWeight * recencyDecay`，其中 recencyDecay 是以 15 天为半衰期的指数衰减。一个创建后从未使用的 Skill，15 天后得分衰减到约 0.2，30 天后约 0.1。`CuratorDaemon` 定期扫描所有 Skill，对低分 Skill 执行合并（`PrefixClusterCurator` 将前缀相似的 Skill 聚类）、降级或清除。说白了，这是一个自维护的知识库，不是简单的 CRUD。进化不只是能力的增长，也包括能力的修剪。

### 为什么进化必须是可选的

一个决定性的架构决策：kairo-core 对 kairo-evolution **零依赖**。

刻意为之，不是疏漏。从 classpath 移除 `kairo-spring-boot-starter-evolution` = 完全禁用进化。没有残留的接口调用、没有空的 if 分支、没有 `evolution.enabled=false` 的配置项。当进化模块不在 classpath 上时，核心运行时甚至不知道进化这个概念存在。

原因很简单：不是所有环境都欢迎 Agent 自我进化。在金融监管领域，如果 Agent 在生产环境中自主创建了一个 Skill 并用于交易决策，监管机构会问"这个决策逻辑是谁批准的"，答案是"Agent 自己决定的，昨天凌晨"——这在大多数受监管行业中不可接受。

所以进化必须是物理级的可移除——和 Linux 内核的 SELinux 一样，不需要它的系统编译时排除即可，内核不会因为 SELinux 不存在而功能退化。

---

## Part 3：Plugin 生态

### 格式兼容的战略决策

自我进化是 Agent 从内部学习。问题在于，一个框架的生命力不取决于内核有多聪明，而取决于生态有多丰富。

Linux 赢了 Minix，不是因为内核更优雅——是因为它有驱动生态。Android 赢了 Symbian，不是因为操作系统更好——是因为它有 Google Play。一个框架的生死取决于生态，不取决于内核。

所以 Kairo 必须有一个 Plugin 系统。面临的第一个战略问题不是技术问题，而是生态问题：定义自己的 Plugin 格式，还是兼容已有的格式？

2026 年中，Claude Code 拥有最大的 Agent Plugin 生态。它的 Plugin 格式——`plugin.json`、`skills/<name>/SKILL.md`、`commands/*.md`、`agents/*.md`、`hooks/hooks.json`、`.mcp.json`——已在社区中被广泛采用。

Kairo 的决策记录在 ADR-029 中，结论明确：**格式兼容，不做目录兼容。** Kairo 读取与 Claude Code 完全相同的文件格式，文件内容不需要任何修改。但 Plugin 安装在 `.kairo-plugin/` 目录而非 `.claude-plugin/`——框架有自己的命名空间。迁移成本是一行命令：`mv .claude-plugin/ .kairo-plugin/`。

逻辑很简单：格式是技术契约，目录是品牌标识。技术契约应该共享（减少适配成本），品牌标识应该独立（保持身份清晰度）。

### 29 个事件映射与变量兼容

格式兼容中最复杂的部分是 Hook 事件的映射。Claude Code 定义了 29 个 Hook 事件名（`PreToolUse`、`PostToolUse`、`SessionStart`、`Stop` 等），Kairo 有自己的 `HookPhase` 枚举。`HookEventMapper` 是桥接的单一真相源——例如 `"Stop"` 映射到 `HookPhase.PRE_COMPLETE`，因为 Claude Code 的 "Stop" 语义是"Agent 主循环即将返回最终答案"，对应 Kairo 生命周期中的 `PRE_COMPLETE` 阶段。映射器支持双向识别，Claude Code 原生 Plugin 和 Kairo 原生 Plugin 可以在同一个运行时中共存。

变量兼容同样透明。`PluginVariableResolver` 同时绑定两套变量名——`${KAIRO_PLUGIN_ROOT}` 是规范名，`${CLAUDE_PLUGIN_ROOT}` 是兼容别名，解析到完全相同的路径。Plugin 作者不需要知道自己的 Plugin 运行在哪个运行时中。

五种安装源已全部实装：LocalPath（开发调试）、GitHub（API 下载 tarball）、GitUrl（克隆仓库）、GitSubdir（marketplace 仓库中的子目录）、Npm（tarball + SHA-1 校验）。缓存策略统一为 `~/.kairo/plugins/cache/<type>/<sha8>/`，数据目录为 `~/.kairo/plugins/data/<plugin-name>/`——Plugin 更新不会丢失数据。

组件注册是原子的——要么全部成功，要么按 LIFO 顺序逐一撤销。注册顺序确定性：`skills → commands → agents → hooks → mcp → bin → outputStyles`，反映了组件之间的依赖关系。

`ClaudeCodeCompatTest` 加载了五个来自 Claude Code 官方仓库的真实 Plugin——原封不动，没有修改一个字节——验证了格式兼容不是理论承诺，而是工程事实。

### Marketplace = Git 与五条不可协商原则

ADR-029 明确了 Marketplace 的策略：git 仓库，不是托管服务器。在 Plugin 生态还不成熟的阶段，运营 Marketplace 服务器的成本（审核、安全扫描、可用性保障）远超收益。git 仓库提供了版本管理（tag）、发现（README + 索引）和分发（clone / API），足以支撑早期生态。当 Marketplace 文件（`marketplace.json`）出现在 Plugin 安装路径中时，`PluginLoader` 会拒绝将其作为单个 Plugin 加载，并提示用户安装其中的具体子目录——避免常见的用户错误。

整个 Plugin 系统的设计凝结为五条不可协商的原则：

1. **格式兼容，不做目录兼容。** 读 Claude Code 的文件格式，安装到 Kairo 的命名空间。
2. **Plugin 贡献到现有注册表，不定义平行的注册表面。** Plugin 的 Skill 注册到框架的 `SkillRegistry`，Plugin 的 Hook 映射到框架的 `HookPhase`。不存在"Plugin 专用的 Skill 列表"——只有一个统一的运行时。
3. **原子组件注册，失败即回滚。** 部分注册是不可接受的状态。
4. **变量名兼容性保留。** `${CLAUDE_PLUGIN_ROOT}` 永远有效，作为 `${KAIRO_PLUGIN_ROOT}` 的别名。
5. **Marketplace = git。** 在生态成熟之前，不运营托管服务。

这五条不是事后总结的最佳实践，而是在设计之初就确定的约束条件。它们限制了解空间，但也加速了实现——当你知道什么不能做时，你才能快速决定应该做什么。

### 进化与 Plugin 的交汇

自我进化和 Plugin 看似独立，但它们在一个深层点上交汇：**它们最终注册到同一个运行时。**

进化出来的 Skill 和 Plugin 提供的 Skill 注册到同一个 `SkillRegistry`。进化的治理 Hook 和 Plugin 的事件 Hook 映射到同一套 `HookPhase`。无论能力来自 Agent 的自学习、社区的 Plugin 贡献、还是企业的内部开发——它们都是框架运行时中的一等公民，遵循同样的生命周期和安全检查。

两者也可以互相增强：Agent 在使用某个 Plugin 时发现了有效的使用模式，进化管线可以将其提取为新的 Skill；一个反复被进化出来的通用 Skill，社区成员可以将它打包为 Plugin 分享给所有用户。

**进化是内部的能力生长。Plugin 是外部的能力供给。** 一个成熟的 Agent 生态同时需要两者。

---

## 系列收束

写到这里，回头看整个系列，最大的感受不是"我做了多少"，而是"还有多少没做"。

回到第一篇文章的开头。一个 20 步的 Agent，每步 90% 成功率，端到端成功率是 3.9%。这个数学事实是整个系列的起点——Agent 的可靠性不是智能问题，而是基础设施问题。

十篇文章，我从不同的角度，拆解了这个基础设施的每一个紧要组件。

第一篇，我从那个令人清醒的数学事实出发，论证了 Agent 需要一个操作系统——VILA-Lab 的逆向分析显示 Claude Code 51.2 万行代码中 98.4% 是确定性基础设施。

第二篇，我拆解了上下文压缩——6 阶段管线，从 Snip 到 CircuitBreaker，让 Agent 不会因为记忆爆满而停止思考。

第三篇，我构建了安全与韧性的纵深防御——六层循环检测、三态熔断器、工具安全六层免疫系统，从副作用分类到 MCP 默认拒绝，让 Agent 在生产环境中不失控、不被注入、不级联崩溃。

第四篇，我追问了工具设计的哲学——56 个工具，11 个分类，Schema 即 Prompt，Side-Effect 分类决定并行与审批。

第五篇，我展开了 SPI 的设计哲学——扩展性让 20+ 个接口皆可替换，极简主义让每个抽象都必须证明自己。一体两面，缺一不可。

第六篇，我进入了 Hook 治理——30 个生命周期点 × 5 种决策值，INJECT 能在 Agent 即将完成时强制追加一轮思考。

第七篇，我讨论了长任务——DurableExecution、Worktree 隔离、检查点恢复，让 Agent 能处理跨越数小时的复杂工程任务。

第八篇，我把 kairo-code 作为 dogfooding 案例——一个用 Kairo 自身构建的 coding agent，56 个工具、Plan 模式、成本追踪，证明框架在真实场景中的价值。

第九篇，我剖析了多智能体的全貌——从 Subagent 的上下文隔离，到 Team 的协作编排，到 Expert Team 的 Plan-Generate-Evaluate 三阶段管线，再到 ExpertTeamCanvas 的实时可视化。同时我坦诚了一个不受欢迎的真相：大多数场景下，你不需要多 Agent。

今天这最后一篇，我看到了 Agent 能力的三个前沿方向：跨越机器边界的分布式协调、从经验中自我学习的进化机制、以及让 Agent 站在社区肩膀上的 Plugin 生态。

所有这些——压缩、安全、工具、扩展、治理、长任务、编排、进化、生态——都是同一个问题的不同侧面：

**当 Agent 从 demo 走向生产时，它需要什么样的基础设施？**

答案和五十年前一样。

1991 年，Linus Torvalds 不是因为写了一个更好的 shell 而改变了计算机历史。他写了一个内核——一个管理进程、管理内存、管理设备、管理安全的运行时系统。Shell 只是内核之上的一个应用。

2026 年，Agent 领域面对的是同样的分水岭。模型在变强——Claude 4、GPT-5、Gemini Ultra——每一代都在刷新推理基准。但模型是 Agent 的 CPU，不是 Agent 的全部。一个只有 CPU 的计算机什么也做不了。它需要内存管理，需要中断处理，需要文件系统，需要安全机制，需要驱动生态。

你不会在写代码的时候想到 Linux 的页面置换算法。你不会在听音乐的时候想到 macOS 的音频驱动调度策略。你不会在浏览网页的时候想到 TCP 拥塞控制的实现细节。操作系统隐身于每一个用户交互的背后，沉默地管理着一切复杂性。

Kairo 的目标也是如此。当一个 Agent 在稳定运行的时候，你不应该想到上下文压缩引擎在后台默默回收空间。你不应该想到循环检测器在每一次工具调用后检查行为模式。你不应该想到 Hook 管线在每一次模型调用前执行安全检查。你不应该想到进化管线在每一次会话结束后审查执行痕迹。

你应该只看到一个 Agent 在工作。可靠地、安全地、高效地工作。

几个月的密集开发，31 个模块，2500+ 个测试。有些深夜调试 Agent 死循环的经历，现在回想起来居然带着某种奇怪的怀念。这个过程改变了我对"框架"这个词的理解——它不该是一堆抽象接口的集合，而是你每天靠它吃饭的东西。

一个好的操作系统，不让你感知它的存在，但让一切成为可能。

---

*全系列完。*

**参考**

1. VILA-Lab, "Dive into Claude Code: The Design Space of Today's and Future AI Agent Systems," arXiv:2604.14228, April 2026
2. amux.io, "Parallel AI Coding Agents with Git Worktree Isolation," 2026
3. Temporal Technologies, "Durable Execution for AI Agents," 2025
4. LangGraph, "LangGraph Cloud: Scalable Agent Deployment," 2026
5. Linus Torvalds, "Tech Talk: Git," Google, 2007
6. Kairo Evolution Module, `io.kairo.evolution.*`, Self-Evolution Pipeline Implementation, 2025-2026
7. Kairo Plugin Module, `io.kairo.plugin.*`, Claude Code Format Compatible Plugin System, 2025-2026
8. ADR-029: Plugin SPI with Claude Code Format Compatibility, Kairo Architecture Decision Records, 2026
9. Anthropic, Claude Code Plugin Format Specification, 2025-2026
10. OWASP, "Agentic AI Top 10 Threats," 2026 Edition
