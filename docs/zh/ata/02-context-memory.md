# 上下文是有限的——Agent 的内存管理问题

*六阶段压缩引擎的设计哲学*

你让 Agent 重构一个 2000 行的 Java 文件。

它先读取源码，分析了类的继承关系和依赖图谱。然后制定了方案——提取三个内部类为独立文件，重命名四个方法，统一异常处理策略。它开始修改第一个方法，运行测试，发现一个间接依赖需要同步更新，于是又读取了三个关联文件。

然后它停了。

下一步，它重新分析了类的继承关系。提出了一个和五分钟前完全一样的重构方案。它不记得自己已经改过第一个方法了，不记得那三个关联文件，不记得自己的计划。

这不是幻觉（hallucination），是失忆。两者的根因完全不同：幻觉是模型生成了不存在的信息——生成问题；失忆是模型丢失了已有的信息——存储问题。在我的经验里，Agent 在长任务中遭遇失忆的频率远高于幻觉。

---

## 上下文窗口就是 RAM

1980 年代，个人计算机的物理内存是 640KB。Bill Gates 说（或者没说）："640KB 应该够任何人用了。" 然后程序越来越大，640KB 不够了。操作系统的回答是虚拟内存：把不常用的内存页交换到磁盘，需要时再换回来。LRU（Least Recently Used）、LFU（Least Frequently Used）——一系列页面替换算法诞生。

2024 年，Claude 3.5 的上下文窗口是 200K token。看起来很大。但你让它读一个 2000 行的 Java 文件，就消耗了大约 8000 token。一个中等规模的重构任务需要读 20 个文件——仅仅是读取，就消耗了 160K token，占满窗口的 80%。还没推理，还没修改代码，还没运行测试，还没解析错误输出。

上下文窗口就是 RAM。物理内存有限需要虚拟内存和页面替换，上下文窗口有限需要压缩和分阶段回收。

只是上下文压缩比内存管理难一个数量级。操作系统的页面替换是无损的——换出去的页面一个比特都不会变，换回来还是原样。上下文压缩是有损的——你压缩一段对话历史，必然丢失信息。问题不是"要不要丢"，是"丢哪些，留哪些"。这个选择没有标准答案，每种丢法都有代价。

Code Agent 把这个问题推到了极端。对话 Agent 处理短文本，用户消息通常不超过 200 token。RAG Agent 检索文档片段，每段几百 token。Code Agent？读一个源文件 5000 token，编译输出 3000 token，测试结果 2000 token，grep 搜索 4000 token。每一轮工具调用消耗的上下文是对话 Agent 的 10 到 50 倍。

第一篇定义的验证死亡螺旋在这里表现得最明显——每一轮修复-验证循环，上下文只增不减。三轮之后，窗口已经被测试输出填满了，Agent 忘记了最初的需求是什么。

上下文管理对 Code Agent 是底线需求。没有压缩引擎的 Code Agent，在第三个文件就撞上窗口限制。有压缩但设计粗糙的 Agent，会在压缩时丢掉决定性的信息——比如"为什么选方案 A 而不是方案 B"。

操作系统用了几十年打磨内存管理，Agent 的上下文管理才刚起步。

---

## 行业怎么做的

### Claude Code: 五层压缩管线

VILA-Lab（新加坡国立大学）在 2026 年 4 月发布了一篇对 Claude Code 的系统性逆向工程研究（arXiv:2604.14228）。他们发现 Claude Code 在上下文管理上投入了大量工程。

Claude Code 使用 5 层压缩管线（compaction pipeline）：预算削减（budget reduction） → 片段裁剪（snip） → 微压缩（microcompact） → 上下文折叠（context collapse） → 自动摘要（auto-compact）。压缩触发的时机是上下文达到约 167K token（200K 窗口 - 20K 预留输出 - 13K 安全缓冲）。

这套设计的核心是渐进式压缩（progressive compaction）：先尝试轻量策略，只在压力持续升高时才升级到更重的策略。每一层只删除信息价值最低的内容——旧的工具输出首先被删，最近的对话历史最后被压缩。

Claude Code 做对的一件事是把压缩做成了管线（pipeline），而不是一次性操作。后面会看到，这个选择对整套方案的成败有多大影响。

### Mem0: 独立记忆层

Mem0 的思路完全不同。它在 Agent 外部维护一个独立的记忆层——从对话中提取关键信息，存入结构化的记忆数据库。下次对话时通过语义检索找回相关记忆。

这个方案解决了跨会话记忆的问题，但有一个我认为比较根本的限制：存储的是摘要，不是原文。"Agent 决定用方案 A 重构"是一条有效的记忆。但方案 A 的细节——哪些方法要移动到哪个类、接口签名如何变化——在摘要中丢失了。对于 Code Agent，这些细节往往就是成败的关键。

### LangChain: 文档级 RAG

LangChain 的上下文管理主要依赖 RAG（Retrieval Augmented Generation）。文档被切片、向量化、存入向量数据库，需要时按语义相似度检索。

RAG 擅长文档级别的知识检索，但会话级别的上下文管理是另一个问题。会话有时间线——"先做了 X，发现问题 Y，于是改为方案 Z"。RAG 的语义检索不保留这种时间因果关系。你搜索"重构方案"，可能同时检索到被放弃的方案 A 和当前的方案 Z，分不清先后。

### Morph FlashCompact: Observation Masking

2025 年，Morph 团队发表了 FlashCompact 研究。他们发现了一个有用的启发式规则——Observation Masking：优先删除工具输出（observation），保留推理过程（reasoning）和决策（decision），对任务完成率的影响最小。

想想也合理：一次 `cat` 命令读出 200 行代码，真正有价值的可能只有 10 行。删除工具输出、保留"我读了文件 X，发现函数 Y 的签名是 Z"这样的摘要，几乎不影响 Agent 的后续决策。

### TALE: Token Elasticity Zones

ACL 2025 收录的 TALE 论文提出了 Token Elasticity Zones——不同类型的 token 对压缩的敏感度不同。系统提示（system prompt）是刚性的，不能压缩；工具输出是弹性的，可以大幅压缩；推理过程介于两者之间。

这个发现为渐进式压缩提供了理论支撑：按弹性从高到低的顺序压缩，每一步损失的有效信息量最小。

### Kiro CLI: 至少让用户看到

Amazon 的 Kiro CLI 做了一件多数 Agent 没做的事：从一开始就显示上下文使用量。用户能看到当前消耗了多少 token、还剩多少、何时会触发压缩。

看起来是小功能，但大多数 Agent 完全不展示上下文压力——用户只会在 Agent 突然"失忆"时才意识到出了问题。可见性是管理的前提，这个道理在基础设施领域应该不用多说。

---

## Kairo 的六阶段压缩引擎

Kairo 的上下文压缩引擎（`ContextCompactionEngine`）实现了六个阶段的渐进压缩。每个阶段都是一个独立的 `CompactionStrategy` SPI 实现，可以单独替换。管线由 `CompactionPipeline` 编排，按优先级从低到高依次执行。

设计原则：渐进压缩，按需升级，事实优先，语义保真。

### 第一阶段: Snip（80% 压力触发）

最轻量的压缩。找到最旧的工具结果（tool result），用占位符替换其内容。保留最近 5 个工具结果不动。

```java
// SnipCompaction 的核心逻辑
String snippet = "[Tool result snipped - " 
    + toolName + " at " + msg.timestamp() + "]";
```

不需要 LLM，不需要网络调用，纯确定性操作。一个 `grep` 结果可能占 4000 token，snip 后只剩 20 token。压缩比很高，信息丢失很低——因为旧的工具输出几乎不会被再次引用。

为什么阈值选 80% 而不是 70% 或 90%？说实话，这个数字是调出来的。太早触发会丢掉可能还有用的工具输出；太晚触发，留给后续阶段的缓冲空间不够。80% 是我们在几十个场景下反复跑出来的一个还算稳定的甜点。

### 第二阶段: Micro（85% 压力触发）

比 Snip 更激进。清除所有工具结果的详细内容，但保留结构信息：工具名、执行状态（成功/失败）、输出大小。

```
[Result: read_file - success - 12480 bytes]
```

Agent 仍然知道"我读过这个文件，成功了，内容很长"，但具体内容没了。这一阶段同时压缩思考内容（ThinkingContent），把冗长的推理过程替换为摘要。

这里有个微妙的 trade-off：保留结构信息看起来成本不高，但这些元数据积少成多。在一个 100+ 工具调用的长会话中，光 `[Result: ...]` 占位符就能占到几千 token。我们试过完全不保留结构信息，但发现 Agent 会困惑——它不知道自己之前是否读过某个文件，导致重复读取，反而浪费更多上下文。

### 第三阶段: Collapse（90% 压力触发）

消息组折叠。把连续的工具调用序列（assistant 调用工具 + tool 返回结果）合并成一条摘要消息。5 次连续的文件读取变成：

```
[Collapsed: 10 tool calls (read_file x5, write_file x3, bash x2) - all successful]
```

10 条消息变 1 条。消息数量的急剧减少同时降低了后续处理的开销。

### 第四阶段: Auto（95% 压力触发）

LLM 生成的结构化摘要。这是第一个需要模型调用的阶段。Kairo 将整个对话历史发送给模型，要求按 9 个维度生成摘要：

1. 当前任务目标
2. 已完成步骤及结果
3. 活跃文件列表（路径和关键内容）
4. 关键决策及推理过程
5. 遇到的问题和解决方案
6. 当前进度
7. 下一步计划
8. 重要代码片段（原文保留）
9. 用户偏好和约束

摘要替换掉中间的对话历史，只保留系统消息和最近 3 条非系统消息。如果模型调用因输入太长而失败，引擎会截掉最旧的 20% 消息后重试，最多重试 3 次。

这一阶段受熔断器（circuit breaker）保护。连续 3 次失败后熔断器打开，30 秒冷却期内跳过 Auto 阶段。压缩引擎本身不能成为新的故障源——这是一个容易被忽视的原则。我们在早期就遇到过压缩调用 LLM 超时，导致整个 Agent 卡住的情况。

### 第五阶段: Partial（98% 压力触发）

最后的安全网。保留三样东西：系统消息、标记为 verbatim 的消息、最后 5 条对话消息。其余全部压缩成一条简短摘要。

这一阶段支持两种方向：FROM（从头开始压缩到尾部保留区域）和 UP_TO（压缩到指定的边界标记）。UP_TO 模式允许 Agent 在对话中放置"检查点"——标记某个位置之后的内容比之前的更重要。

### 第六阶段: CircuitBreaker（99% 压力触发）

紧急全量重置。当前五个阶段全部失败或不足以降低压力时，丢弃除系统消息外的所有历史。丢失全部会话上下文，但总比 Agent 因为上下文溢出而完全停转要好。

用类比来说，这是"拔电源重启"。谁都不想走到这一步，但必须有这一步。

![6-Stage Context Compaction Pipeline](../../public/images/ata/compaction-pipeline.png)

### 设计哲学: 事实优先

六个阶段不是随意排列的。背后有一条原则：事实优先（Facts First）——尽可能晚地压缩，尽可能多地保留原始信息。

**rawContent 双存储机制。** `MemoryEntry` 同时存储 `content`（压缩后的摘要）和 `rawContent`（压缩前的原文）。当 `CompactionTrigger` 触发压缩时，它先把当前的完整对话序列化为 rawContent，然后才执行压缩。结果是即使对话历史被压缩到只剩一条摘要，原始的完整对话仍然存在于记忆系统中。

相当于会话级别的"时间旅行调试"（time-travel debugging）——可以在任何时刻回溯到压缩前的状态。

**Verbatim 标记 = 内存页锁定。** 操作系统允许关键内存页被"锁定"（pinned）——永远不会被交换到磁盘。Kairo 的 `verbatimPreserved` 标记做的是同一件事。标记为 verbatim 的消息在所有压缩阶段中都被跳过。系统提示、用户的根本需求、决定性的决策——这些信息的丢失代价太高，必须锁定。

```java
// CompactionPipeline.execute() 的第一件事：过滤 verbatim 消息
List<Msg> compressible = messages.stream()
    .filter(m -> !verbatimIds.contains(m.id()))
    .toList();
```

这里有个实际操作中的困难：谁来决定哪些消息是 verbatim 的？目前主要靠规则（系统消息自动锁定、用户消息的第一条自动锁定），但 Agent 在运行中产生的关键决策——比如"经过比较我选择方案 B"——目前没有自动标记机制。这意味着关键决策有可能在压缩中被丢掉。我们的做法是在 Auto 阶段的摘要提示词中特别强调保留决策推理，但这依赖 LLM 的判断力，不是一个让人特别放心的方案。

**CompactionStrategy SPI。** 每个压缩阶段实现同一个接口：`shouldTrigger(ContextState)` 决定是否触发，`compact(List<Msg>, CompactionConfig)` 执行压缩，`priority()` 决定执行顺序。任何阶段都可以被替换——你可以用自己的实现替换 AutoCompaction，比如用一个更便宜的模型，或者用 RAG 检索代替 LLM 摘要。

每一层的阈值（80%、85%、90%、95%、98%）都通过 `CompactionThresholds` record 外部化。用户可以只覆盖其中一个值，其余保持默认。

**混合阈值触发。** `HybridThreshold` 同时检查百分比阈值和绝对 token 缓冲。对于 200K 窗口，80% 是 160K token；但如果模型的窗口只有 32K，80% 是 25.6K——此时绝对缓冲（如 40K token）会先触发。这确保在小窗口模型上，压缩也能及时启动。

**Hook 集成。** `CompactionPipeline` 在压缩前后触发 `PreCompactEvent` 和 `PostCompactEvent` hook。外部系统可以在压缩前注入消息（比如"即将压缩，请确认关键信息"），也可以在压缩后追加恢复消息。hook 甚至可以通过 ABORT 决策完全阻止一次压缩。

---

## 上下文工程的经济学

压缩引擎解决了"上下文不够用"的问题。但还有一个同等重要的问题：上下文的每一个 token 都有成本。

### Prompt Cache：10 倍的成本差异

Claude 和大多数 LLM 提供了 prompt cache 机制。当连续两次 API 调用的 system prompt 相同时，第二次调用只需要支付 cache read 的价格——大约是 cache miss 的十分之一。

这个优化的规模比直觉感受到的要大。一个典型的 Code Agent 会话包含几十次模型调用，system prompt 在每次调用中都完全相同（工具定义、角色指令、安全规则）。如果 prompt cache 始终命中，system prompt 的成本从 $3/M token 降到 $0.30/M token。对于一个 11,000 token 的 system prompt，每次调用节省 $0.03——30 次调用就是 $0.9。一天 100 个会话，一年省下的是真金白银。

直接的推论：系统提示的稳定性直接影响成本。每次改动 system prompt 的一个字符，都会导致 cache miss。Claude Code 的 prompt 工程中大量使用 `cache_control` 标记来精确控制缓存边界。Kairo 的做法类似：静态部分（工具 schema、基础指令）在前，动态部分（会话状态、进化记忆）在后，确保静态前缀始终命中缓存。

### CompactionModelFork：为什么压缩不能用主模型

这里有一个不太直觉的设计问题。当 AutoCompaction 需要调用 LLM 来生成摘要时，它能直接复用主对话的 ModelProvider 吗？

不能。原因是 prompt cache 隔离。

主对话的 system prompt 包含 56 个工具的 schema、角色指令、安全规则——约 11K token，且每次调用都缓存命中。如果压缩调用也走同一个 ModelProvider，它会带一个完全不同的 system prompt（压缩指令，不含工具 schema），导致主对话的 prompt cache 被污染。下一次正常模型调用就会 cache miss——11K token 的缓存白建了。

Kairo 的解决方案是 `CompactionModelFork`。它构建一个独立的消息列表，使用不同的 system prompt（9 维度摘要指令），temperature 0.3（确定性摘要），无工具，最大 20K token。压缩调用和主对话走不同的缓存通道，互不干扰。

代码量只有几十行，但它保护的是整个会话的缓存经济性。我们发现这个问题的过程本身也挺有教育意义——最初压缩和主对话共用 ModelProvider，结果每次压缩完，下一轮对话的延迟就会跳一截。排查了半天才意识到是 cache miss 导致的。

### Token Budget Manager：一个真实的 bug story

在 Kairo 的早期版本中，我遇到了一个诡异的 bug：压缩引擎从不触发。上下文窗口明明已经 90% 满了，压缩管线始终认为压力是 0%。查了两天。

原因挺荒唐：系统中存在两个独立的 TokenBudgetManager 实例。一个跟踪 API 返回的 token 用量，供 IterationGuards（硬性上限）使用；另一个供 CompactionTrigger（压力触发）使用。API 返回的 token 数据只更新了第一个实例，第二个实例的压力值永远是零。

修复只需要一个 `instanceof` 检查——如果 ContextManager 是 `DefaultContextManager`，就从中提取已有的 `TokenBudgetManager` 实例，而不是创建新的。代码里留了一行注释：`see: fix-dual-tokenbudget`。

这个 bug 的教训很具体：在 Agent 运行时中，token 计数是最要命的共享状态。任何组件如果有自己的 token 计数器副本，迟早会出问题。Kairo 现在使用单一实例模式——IterationGuards、CompactionTrigger、CostTracker 都读同一个原子计数器。看起来是个基本的工程原则，但在运行时系统越做越复杂的过程中，这种"不该有两份"的东西确实容易不小心多出来一份。

### Cost-Aware Routing：用对的模型做对的事

不是所有任务都需要最贵的模型。读取一个文件的内容？Haiku 就够了。规划一个复杂的重构？需要 Opus。

Kairo 的 `RoutingPolicy` SPI（ADR-013）实现了成本感知路由。`ModelTierRegistry` 将模型分为层级，`CostAwareRoutingPolicy` 根据任务复杂度、上下文大小和预算约束选择最合适的模型。降级是线性的——如果首选模型的价格超出预算，自动降到下一级。

目标是 60-80% 的成本节省，同时保持输出质量。但老实说，"任务复杂度"的判断目前还比较粗糙——我们主要靠上下文大小和工具调用类型来估算，远没有做到真正的语义级复杂度评估。这块还有不少空间。

### 11,000 Token 的固定税

还有一个上下文经济学话题值得提及：56 个工具的 JSON Schema 在系统提示中占了约 11,000 token 的固定开销。这笔"税"与 prompt cache 的交互、动态工具加载的可能性，将在第四篇《工具是 Agent 的系统调用》中详细讨论。

---

## 压缩引擎的盲区

写完六阶段引擎，值得停下来想一个问题：为什么不直接用 RAG？

RAG 的优势摆在那里——把所有对话历史存入向量数据库，每次模型调用前检索最相关的片段。不需要复杂的分阶段压缩，不需要 LLM 生成摘要。简单、高效、可扩展。

问题在于 RAG 解决的是知识检索问题，会话记忆是另一回事。会话记忆有一个 RAG 不太擅长的特性：时间因果性。"我先尝试了方案 A，发现问题 X，所以切换到方案 B"——这三条信息必须按时间顺序出现在上下文中，Agent 才能理解当前状态。如果 RAG 只检索到"方案 B"而没有检索到"放弃方案 A 的原因"，Agent 可能会走回头路。

Kairo 选择渐进式压缩就是因为这个考量。压缩保留了时间线和因果链，只减少了每条消息的信息密度。

为什么 6 个阶段而不是 1 个？一次性摘要（one-shot summarization）听起来更简单：把整个对话扔给 LLM，让它生成一个摘要。但模型不知道哪些信息对后续步骤是关键的。一段 200 行的 `grep` 输出和用户说的一句"不要改 public API"——在原始文本中，后者只占 0.05% 的 token，但信息价值可能是前者的一千倍。

渐进压缩的每一阶段都在做信息分拣：先丢弃信息密度最低的工具输出（Snip），再丢弃工具结果的详细内容但保留结构（Micro），再折叠重复的工具调用模式（Collapse），最后才动用 LLM 做语义级压缩（Auto）。四个确定性阶段过滤掉大约 80% 的噪音后，LLM 只需要处理真正有价值的部分。这也是为什么 Auto 阶段的摘要质量明显比直接对原始对话做摘要要好——输入干净了，输出自然也干净。

不过有几个问题我还没解决。

跨会话记忆的连续性是最大的一个。一个持续三天的重构任务，每天启动新会话。第二天的 Agent 不知道第一天做了什么——除非手动把前一天的摘要贴到新会话里。`MemoryStore` 的 rawContent 机制保留了数据，但目前没有自动机制把跨会话的记忆注入到新会话的上下文中。

语义压缩还有一个更根本的限制：你无法在不理解语义的前提下完美压缩语义。LLM 生成的摘要总是有偏差——它可能认为某个细节不重要而省略，但那个细节恰好是后续步骤的关键前提。这是信息论的硬约束，工程上能缓解但不能消除。我能做的是：让压缩尽可能晚发生（事实优先），让致命信息可以被锁定（verbatim），让原始数据可以被回溯（rawContent）。

上下文窗口会继续变大——一百万、两百万、一千万 token。但有限就是有限，只要有限就需要管理。就像内存从 640KB 涨到 128GB，操作系统的内存管理从未消失过。

*下一篇：《Agent 在生产环境中的安全与韧性——从循环检测到纵深防御》*

## 参考文献

1. VILA-Lab, "Dive into Claude Code: An Empirical Study on a Production AI Coding Agent," arXiv:2604.14228, April 2026
2. Morph, "FlashCompact: Observation Masking for Agent Context Compression," 2025
3. TALE, "Token Elasticity Zones for Progressive Context Compression," ACL 2025
4. Anthropic, "Context Engineering Guidelines," 2026
