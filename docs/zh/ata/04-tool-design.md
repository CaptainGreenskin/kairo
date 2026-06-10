# 工具是 Agent 的系统调用——56 个工具的设计哲学

*粒度、Schema 与 Side-Effect*

---

给 Agent 一个 bash 工具，它就能做任何事。

`ls`、`grep`、`cat`、`sed`、`git`、`curl`——Unix 哲学教会我们的第一课是：一切皆文件，一切皆命令。一个 bash 工具就是一扇通往整个操作系统的门。模型可以自由组合任何命令，完成任何任务。

问题是代价。

考虑这样一个场景：Agent 调用 `bash("sed -i 's/foo/bar/g' src/main/java/App.java")`。从模型的视角看，这是一个完美的工具调用——语法正确，意图明确。但从运行时的视角看，这是一个黑盒：

- 这个命令会不会修改文件？会。
- 修改了几处？不知道。
- 修改前的内容是什么？不知道。
- 如果 `foo` 出现了 47 次，其中 3 次不该改呢？不知道。
- 如果文件不存在呢？会静默失败，创建一个空文件。
- 如果这个文件在 `.git/hooks/` 下面呢？安全策略无法拦截，因为运行时看到的只是一个字符串。

一个 bash 工具的输入是字符串，输出是字符串。模型拥有最大的灵活性，运行时拥有最少的信息。相当于一个操作系统把所有系统调用合并成了一个——`syscall(string)`——用户态程序可以做任何事，内核无法做任何保护。

## 粒度困境

工具设计的根本矛盾是一个频谱的两端。

### 太粗：1 个 bash 工具

一端是极致的粗粒度。只给模型一个 bash 工具，让它自己组合命令。

灵活性拉满，但代价也很大：

运行时没有任何安全元数据。它无法区分 `cat README.md`（只读）和 `rm -rf /`（灾难性）。每一次调用都可能是任何事情。权限策略只能依赖字符串模式匹配——而命令行的组合空间是无穷的。`rm` 可以藏在 `xargs` 里，可以藏在 `eval` 里，可以藏在 `$(...)` 里。任何基于正则的防御都是不完备的。

没法并行。因为每个调用的副作用未知，运行时只能串行执行。模型想同时读 5 个文件？对不起，必须依次调用 5 次 bash，每次等待完成。

输出完全非结构化。`ls -la` 的输出需要模型自己解析日期、权限、文件名。`grep` 的输出需要模型自己拆分文件路径和行号。这消耗推理能力，增加幻觉概率。

上下文容易爆炸。一个 `find . -name "*.java"` 在一个大型项目中可能输出 10 万行。模型不知道这会发生，运行时也不知道该怎么截断——因为它不理解输出的语义。

### 太细：100 个原子工具

另一端是极致的细粒度。为每个操作定义一个专用工具：`ReadFileLine`、`ReadFileRange`、`ReadFileAll`、`WriteFileLine`、`WriteFileAppend`、`WriteFileReplace`……

每个工具有精确的 schema，运行时完全理解每个调用的意图和副作用。权限策略可以做到字段级别的精细控制。

但这条路走不通：

上下文税太重。每个工具的 schema（名称 + 描述 + 参数定义）大约消耗 150-250 token。100 个工具就是 15,000-25,000 token 的系统提示——还没开始工作，上下文窗口已经被吃掉了 10-15%。在 200K 窗口的模型上这也许还能忍，但上下文压缩引擎的效率会因为巨大的系统提示而显著下降。

模型选择困难。需要在 100 个工具中选择正确的那个。`ReadFileLine` 还是 `ReadFileRange`？`WriteFileReplace` 还是 `WriteFileAppend`？选错了不会报错，只会产生错误的结果。工具越多，选择越难，幻觉参数的概率越高。

调用链过长。一个人类开发者用一条 `sed` 命令能完成的事，原子工具可能需要 10 次调用：读取文件、定位行号、提取原文、计算替换、写回文件、验证结果……每次调用都消耗上下文，每次调用都引入延迟。

### 甜蜜点：领域工具

合理的位置在中间——领域工具。不按操作系统的原语划分（read/write/seek/stat），而按 Agent 的工作领域划分。

Claude Code 找到了一个平衡点：43 个工具。Bash 存在，但不是首选。当模型需要读文件时，它有 `ReadFileTool`——知道文件路径、知道行号范围、知道输出是结构化的文本。当模型需要搜索代码时，它有 `GrepTool`——知道正则模式、知道搜索目录、知道输出是 `file:line:content` 格式。Bash 是逃生舱——只在专用工具覆盖不了的场景才使用。

Claude Code 的内部数据验证了这一点：当结构化工具可用时，模型会优先使用它们而非 bash。因为结构化工具的 schema 已经告诉模型"这个工具能做什么、怎么用"——模型不需要自己构造命令语法。schema 就是 prompt。

---

## 56 个工具，9 个类别

Kairo 的工具集在 Claude Code 的基础上扩展到了 56 个，覆盖 9 个功能类别。扩展的逻辑不是"越多越好"，而是跟随 Agent 运行时面对的真实需求。

### 文件与代码（14 个）

这是最密集的区域，因为 Code Agent 日常就是读写代码。

| 工具 | 副作用 | 职责 |
|------|--------|------|
| `read` | READ_ONLY | 读取文件，支持行号范围 |
| `write` | WRITE | 写入整个文件 |
| `edit` | WRITE | 精确文本替换（old_string → new_string） |
| `glob` | READ_ONLY | 按 glob 模式搜索文件名 |
| `grep` | READ_ONLY | 按正则搜索文件内容 |
| `tree` | READ_ONLY | 显示目录树结构 |
| `diff` | READ_ONLY | 比较文件差异 |
| `batch_read` | READ_ONLY | 批量读取多个文件 |
| `batch_write` | WRITE | 批量写入多个文件 |
| `search_replace` | WRITE | 跨文件搜索替换 |
| `patch_apply` | WRITE | 应用 unified diff 补丁 |
| `json_query` | READ_ONLY | JSONPath 查询 |
| `template_render` | WRITE | 模板渲染 |
| `lsp` | READ_ONLY | LSP 诊断（编译错误、类型错误） |

`edit` 工具的设计值得展开说。它不是 `write` 的变体，而是一个独立的语义：精确替换。参数是 `originalText` 和 `newText`，而不是行号和内容。

这个选择的好处有几个。首先，唯一性校验：`originalText` 必须在文件中唯一出现，如果出现多次工具直接报错——强制模型提供更多上下文以消除歧义。这比 `sed` 的 `s/foo/bar/g`（静默替换所有匹配）安全一个数量级。我们吃过亏：早期版本用 sed 风格全局替换，有次 Agent 把 47 个 `import` 语句一口气全改坏了。其次，schema 本身就是文档：模型看到 `originalText` 和 `newText` 两个参数，立刻理解意图，不需要学习 `sed` 的正则语法。第三，可审计性：运行时精确知道"替换了什么"和"替换成什么"，可以生成 diff、回滚、写入审计日志。

再说 `lsp` 工具。它不执行任何修改——它调用语言服务器获取诊断信息。当 `edit` 工具修改了一个文件后，`PostEditDiagnosticsHook` 会自动触发 LSP 诊断，将"这次编辑是否引入了新的编译错误"作为元数据附加到工具结果中。Agent 不需要主动调用编译器——编辑操作本身就携带了验证反馈。

```java
@Tool(name = "edit",
      description = "Make precise text replacements in a file. "
                  + "The original text must be unique in the file.",
      category = ToolCategory.FILE_AND_CODE,
      sideEffect = ToolSideEffect.WRITE)
public class EditTool implements SyncTool {

    @ToolParam(description = "The absolute path of the file to edit",
               required = true)
    private String path;

    @ToolParam(description = "The exact text to find and replace",
               required = true)
    private String originalText;

    @ToolParam(description = "The replacement text",
               required = true)
    private String newText;
}
```

这段代码可以多看两眼。`@Tool` 注解上的 `description` 不只是给开发者看的——它会被序列化成 JSON Schema 的一部分，注入模型的系统提示。`@ToolParam` 的 `description` 同理。注解文本本身就是 prompt 工程。写 "The exact text to find and replace" 还是写 "The original text"，直接影响模型填充参数的准确率。

### 命令执行（5 个）

| 工具 | 副作用 | 职责 |
|------|--------|------|
| `bash` | SYSTEM_CHANGE | 通用 Shell 命令 |
| `git` | SYSTEM_CHANGE | Git 操作 |
| `mvn` | SYSTEM_CHANGE | Maven 构建 |
| `monitor` | READ_ONLY | 进程监控 |
| `verify_execution` | SYSTEM_CHANGE | 编译 + 测试验证 |

`bash` 是逃生舱，但不是黑盒。`CommandSafetyPolicy` 对命令做两层分类：

Tier 1 是灾难性命令，无条件拦截。`rm -rf /`、`mkfs`、`dd of=/dev/sda`、fork bomb、`sudo` 前缀。这些模式在进入沙箱之前就被截断。不经过 ApprovalGate，不询问用户，直接返回错误。

Tier 2 是危险命令，需要审批。`git push --force`、`git reset --hard`、`chmod 777 /`、`shutdown`。这些命令触发 `ApprovalGate` 流程——运行时暂停执行，等待人类决策。人类可以批准、拒绝、或修改命令后批准。

```java
// 灾难性检查：无条件拦截
Optional<String> catastrophic = commandPolicy.checkCatastrophic(cmd);
if (catastrophic.isPresent()) {
    return Flux.just(new ToolEvent.Final(error(catastrophic.get())));
}

// 危险检查：触发审批流程
if (commandPolicy.isDangerous(cmd)) {
    return Flux.concat(
        Flux.just(new ToolEvent.NeedsApproval(cmd, reason)),
        gate.get().await(cmd, reason)
            .flatMapMany(decision -> handleDecision(decision, ...)));
}
```

`ApprovalGate.Decision` 是一个 sealed interface——`Approved` 或 `Rejected`，没有第三种可能。`Approved` 可以携带修改后的参数（`editedArgs`）——用户可以在批准前编辑命令。这个设计来自一个真实场景：Agent 想执行 `git push --force origin main`，用户看到后，把 `--force` 改成 `--force-with-lease` 再批准。

`git` 和 `mvn` 是从 `bash` 中剥离出来的专用工具。原因不复杂：`git status` 和 `rm -rf /` 的风险等级天差地别，但在 `bash` 的视角下它们只是两个字符串。给 `git` 一个独立工具，运行时就能做更精细的安全策略——比如允许 `git status`、`git log`、`git diff` 自动执行，但拦截 `git push --force`。

### Agent 与任务管理（20 个）

这是工具数量最多的类别——Agent 的协作模式远比单一的 bash 命令复杂。

| 子域 | 工具 | 数量 |
|------|------|------|
| 计划模式 | enter/exit/list_plans | 3 |
| 子 Agent | agent_spawn, send_message | 2 |
| 任务 | task_create/get/list/update | 4 |
| 团队 | team_create/delete | 2 |
| Todo | todo_read/write | 2 |
| 记忆 | memory_read/write/delete | 3 |
| 团队记忆 | team_memory_read/write/delete | 3 |
| GitHub | github | 1 |

计划模式工具（`enter_plan_mode` / `exit_plan_mode`）解决了一个真实痛点。当 Agent 进入计划模式，所有 WRITE 和 SYSTEM_CHANGE 工具被禁用。Agent 只能读取和思考，不能修改任何东西。这防止了一个常见的失败模式：Agent 在理解问题之前就开始动手改代码，改错了又要修，修了又引入新错误——验证死亡螺旋。

计划模式强制 Agent 先读、先想、先规划，然后退出计划模式再执行。副作用分类使这个设计成为可能——如果没有 READ_ONLY / WRITE / SYSTEM_CHANGE 的三级分类，运行时就无法知道哪些工具该禁用。

记忆工具（`memory_read` / `memory_write` / `memory_delete`）提供跨会话的持久知识。Agent 在这次对话中学到的东西——项目的编码规范、常见的陷阱、团队的偏好——可以写入记忆存储，下次对话直接读取。团队记忆（`team_memory_*`）则在多 Agent 场景中共享知识。

### 调度（8 个）

| 工具 | 副作用 | 职责 |
|------|--------|------|
| `CronCreate` | WRITE | 创建定时任务 |
| `CronEdit` | WRITE | 编辑定时任务 |
| `CronDelete` | WRITE | 删除定时任务 |
| `CronList` | READ_ONLY | 列出定时任务 |
| `CronPause` | WRITE | 暂停定时任务 |
| `CronResume` | WRITE | 恢复定时任务 |
| `CronTrigger` | SYSTEM_CHANGE | 手动触发定时任务 |
| `Sleep` | READ_ONLY | 等待指定时间 |

### 信息检索（4 个）

| 工具 | 副作用 | 职责 |
|------|--------|------|
| `web_search` | READ_ONLY | 搜索互联网 |
| `web_fetch` | READ_ONLY | 获取网页内容 |
| `http_request` | SYSTEM_CHANGE | 发送 HTTP 请求 |
| `ask_user` | READ_ONLY | 向用户提问 |

### 技能（3 个）

| 工具 | 副作用 | 职责 |
|------|--------|------|
| `skill_list` | READ_ONLY | 列出可用技能 |
| `skill_load` | WRITE | 加载技能 |
| `skill_manage` | WRITE | 管理技能 |

### 工作流（1 个）

| 工具 | 副作用 | 职责 |
|------|--------|------|
| `workflow` | SYSTEM_CHANGE | 执行定义好的工作流 |

## Schema 即 Prompt

工具的 JSON Schema 同时做两件事：参数校验和模型引导。

当 Kairo 启动时，每个 `@Tool` 注解的类被扫描，`@ToolParam` 字段被收集，生成一份 JSON Schema。这份 schema 被序列化后注入模型的系统提示。模型每次推理时，都能"看到"所有工具的完整定义。

```json
{
  "name": "edit",
  "description": "Make precise text replacements in a file. The original text must be unique in the file.",
  "input_schema": {
    "type": "object",
    "properties": {
      "path": {
        "type": "string",
        "description": "The absolute path of the file to edit"
      },
      "originalText": {
        "type": "string",
        "description": "The exact text to find and replace"
      },
      "newText": {
        "type": "string",
        "description": "The replacement text"
      }
    },
    "required": ["path", "originalText", "newText"]
  }
}
```

这段 JSON 大约 200 token。乘以 56 个工具，系统提示中光工具定义就占了约 11,000 token。第一次算出这个数字的时候有点发愁——光工具描述就吃掉这么多上下文？验了几遍，数字确实没错。

11,000 token 在 200K 窗口的模型上占 5.5% 的预算。在对话进行到后期、上下文已经被代码和工具输出填满的时候，这 5.5% 可能就是压缩引擎能不能保留关键信息的分水岭。

但这笔账算下来还是值得的——schema 的每一行都在替你做 prompt 工程。

拿 `"description": "The exact text to find and replace"` 这 9 个英文单词举例，它做了三件事：告诉模型这个参数的语义是"原文"，不是行号、不是正则、不是文件名。"exact" 这个词减少了模型"大概猜一下"的倾向。"find" 告诉模型这个文本会被用来定位——所以它必须足够独特。

好的 schema 描述减少幻觉参数，差的 schema 描述导致模型猜测。我在实践中观察到：把 EditTool 的 `originalText` 描述从 `"The text to replace"` 改成 `"The exact text to find and replace"` 后，模型提供不完整原文（缺少空格、换行、缩进）的频率下降了约 30%。一个词的差别，效果差这么多，有点出乎意料。

所以工具的 schema 对模型来说就是 prompt。description 不是给人看的注释——它是给模型看的指令。

`usageGuidance` 字段把这个理念推得更远。它允许在工具描述之外附加使用指南——比如 "Use for quick file reads; for large files use GrepTool instead" 或 "Danger: may modify system state"。这些指南不影响参数校验，但直接影响模型的工具选择策略。

```java
@Tool(name = "read",
      description = "Read the contents of a file. Can read specific line ranges.",
      category = ToolCategory.FILE_AND_CODE,
      usageGuidance = "Use for quick file reads; for large files use GrepTool instead")
```

## 副作用分类与调度后果

工具设计中最重要的元数据不是名称，不是描述，不是参数——是副作用分类。

Kairo 定义了三个级别：

```java
public enum ToolSideEffect {
    /** Safe for parallel execution: Read, Grep, Glob, List. */
    READ_ONLY,
    /** Must serialize: Write, Edit. */
    WRITE,
    /** Must serialize + may need approval: Bash, shell commands. */
    SYSTEM_CHANGE
}
```

三个 enum 值，驱动了整个运行时的调度和安全决策。

### 并行执行

`ToolPartitioner` 根据副作用分类将一批工具调用拆分为两组：parallel 和 serial。

```java
public static Partition partition(
        List<Content.ToolUseContent> toolUses,
        Function<String, ToolSideEffect> sideEffectResolver) {
    List<Content.ToolUseContent> parallel = new ArrayList<>();
    List<Content.ToolUseContent> serial = new ArrayList<>();
    for (Content.ToolUseContent t : toolUses) {
        ToolSideEffect sideEffect = sideEffectResolver.apply(t.toolName());
        if (sideEffect == ToolSideEffect.READ_ONLY) {
            parallel.add(t);
        } else {
            serial.add(t);
        }
    }
    return new Partition(parallel, serial);
}
```

READ_ONLY 工具并行执行，WRITE 和 SYSTEM_CHANGE 工具串行执行。并行完成后再开始串行。最终结果按原始调用顺序合并——模型不知道底层的执行顺序变了。

收益是实在的。一个典型的"理解代码库"任务，模型可能同时请求 5 个 `read` + 3 个 `grep` + 2 个 `glob`——10 个 READ_ONLY 调用。串行执行假设每个调用 50ms，总耗时 500ms。并行执行理论上降到 50ms（实际会高一些，因为 IO 竞争）。

并行执行的前提是运行时知道这些工具不会互相干扰。如果没有副作用分类，运行时不得不保守地串行执行所有调用——因为它不知道 `grep` 会不会偷偷写文件。这就是元数据的价值：一个 enum 值省下了几百毫秒的延迟。

### 权限三态模型

副作用分类直接映射到权限策略：

| 副作用 | 权限默认值 | 自动模式行为 |
|--------|-----------|-------------|
| READ_ONLY | ALLOWED | 自动执行，无需确认 |
| WRITE | ASK | 需要配置白名单或用户确认 |
| SYSTEM_CHANGE | ASK | 必须经过 PermissionGuard + 可能触发 ApprovalGate |

`PermissionGuard` 是一个 SPI——不同的部署场景可以插入不同的实现。开发环境可以宽松一些（允许所有 WRITE 操作）。生产环境可以严格一些（每个 SYSTEM_CHANGE 都需要审批）。CI/CD 环境可以完全自动化（所有操作都允许，但加审计日志）。

`PermissionDecision` 是一个结构化记录：

```java
public record PermissionDecision(
        boolean allowed,
        String reason,    // 拒绝原因（allowed=true 时为 null）
        String policyId   // 哪条策略做的决策
) {}
```

`reason` 告诉 Agent 为什么被拒绝——这样 Agent 可以调整策略重试，而不是盲目地重复同一个操作。`policyId` 提供审计线索。这个 record 的设计看着简单，但少了 `reason` 字段，Agent 碰到拒绝就只能瞎猜原因。

### 计划模式

计划模式是副作用分类一个比较优雅的应用。当 Agent 进入计划模式，运行时只需要一个规则：

> 如果 `sideEffect != READ_ONLY`，拒绝执行。

这一个条件就实现了"只读思考"——Agent 可以读取任何文件、搜索任何内容、查询任何信息，但不能修改任何东西。这个约束是编译时可验证的——每个工具的副作用在注解中声明，运行时不需要猜测。

## 工具结果预算

一个 `grep` 调用在一个大型代码库上搜索 `import`，结果可能包含 10,000 行。把 10,000 行文本塞进对话历史，上下文窗口就爆了。

ADR-010 定义了 `ToolResultBudget` 契约：每个工具结果在进入对话历史之前，必须经过预算治理。

```java
// 默认：100 KB / 工具，300 KB / 轮次
public static final OutputBudgetConfig DEFAULT =
    new OutputBudgetConfig(100_000, 300_000);
```

超出预算的输出被截断，保留可见部分，附加元数据说明：

```text
...[truncated by ToolResultBudget: originalTokens=12500, keptTokens=2048]
```

预算的计算是动态的——根据剩余上下文预算按比例分配，不是固定的截断阈值。

```java
// 总预算 = 剩余上下文 × 35%，限制在 [256, 8192] token
int totalBudgetTokens = boundedTotalBudget(remainingBudget);
// 每条结果预算 = 总预算 / 结果数，限制在 [96, 2048] token
int perResultBudgetTokens = boundedPerResultBudget(totalBudgetTokens, results.size());
```

为什么是 35%？工具结果之后，模型还需要空间来推理和生成下一轮的工具调用。如果工具结果占了剩余上下文的 80%，模型的推理质量会严重下降。35% 是试出来的经验值——一开始设的 50%，发现推理质量掉得明显，调到 30% 又觉得工具信息不够。最终 35% 是个还算平衡的点。

一个容易忽略的细节：预算是按每批结果计算的，不是按单个结果。如果模型在一个轮次中调用了 8 个工具，8 个结果共享总预算。直接后果是同一轮调用越多工具，每个结果分到的预算越少。这个设计有一个隐含的激励效果：鼓励模型一次调用少量工具，分几轮执行，而不是一次性铺开。

截断后的信息不是丢失了——它被标记了。元数据中的 `tool_result_truncated=true` 和 `tool_result_original_tokens` 告诉模型"你看到的是不完整的"和"完整版有多大"。模型可以据此决定是否需要用更精确的参数重新查询。原始截断是不带任何元数据的暴力砍断；预算治理至少让模型知道自己看到的是不完整的，可以决定下一步怎么办。

---

## 行业横向对比

### Claude Code：43 个工具

Claude Code 定义了当前的标杆。43 个工具覆盖文件操作、命令执行、搜索、Web 访问、子 Agent 等领域。Bash 存在但不是首选——当 ReadFileTool、GrepTool、GlobTool 能完成任务时，模型会选择它们。

Claude Code 的工具设计有一个被低估的创新：流式工具执行。在 API 响应尚未完全返回时，已解析的工具调用就开始执行。如果模型在一次响应中产生了 3 个工具调用，第一个工具在第二个被解析出来之前就已经开始运行了。对于 READ_ONLY 工具，这进一步压缩了延迟。

### Cursor：隐式工具

Cursor 的工具嵌入 IDE 内部——对用户不可见，也很少被公开讨论。它的优势是与编辑器状态深度集成（打开的文件、光标位置、选区），但缺少可编程性。你无法自定义工具的行为，无法注入新工具，无法控制工具的权限策略。

### Devin：全环境访问

Devin 的方式接近"一个 bash 工具"的极端——在一个沙箱化的云端 VM 中给 Agent 完全的环境访问。安全通过沙箱隔离而非工具粒度来保证。这适合高自治场景，但放弃了细粒度控制。

### 对比矩阵

| 维度 | Claude Code | Cursor | Devin | Kairo |
|------|------------|--------|-------|-------|
| 工具数 | 43 | 未公开 | ~5 | 56 |
| 副作用分类 | 隐含 | 无 | 沙箱隔离 | 三级枚举 |
| 并行执行 | 有（最多 10 并发） | 未公开 | N/A | 有（按分类） |
| 审批流程 | YOLO 分类器 | IDE 内确认 | 结果审查 | ApprovalGate SPI |
| 结果预算 | 有 | 未公开 | 无上限 | ADR-010 |
| 自定义工具 | 有限 | 无 | 有限 | SPI 扩展 |
| 安全策略 | 双阶段分类器 | 未公开 | VM 沙箱 | CommandSafetyPolicy + PermissionGuard |

## 工具的结构化输出

工具不只返回字符串。Kairo 的 `ToolOutput` 是一个 sealed hierarchy：

```java
public sealed interface ToolOutput {
    record Text(String content) implements ToolOutput {}
    record Structured(Map<String, Object> data) implements ToolOutput {}
    record Binary(byte[] data, String mime) implements ToolOutput {}
    record Truncated(String visible, long totalBytes, Optional<URI> fullOutput)
            implements ToolOutput {}
}
```

`Text` 是最常见的——给模型看的文本。`Structured` 是给程序消费的键值对——可观测性、审计、回放。`Binary` 处理图片、压缩包等二进制数据。`Truncated` 是预算治理的产物——保留可见部分，附带完整输出的 URI。

`ToolResult` 在 `ToolOutput` 之上附加了元数据、结局（`ToolOutcome`）和提示（`Hint`）：

```java
public record Hint(HintLevel level, String message, Optional<String> suggestedFix) {
    public enum HintLevel { INFO, WARNING, ERROR }
}
```

`Hint` 是一个容易被忽略但实际很有用的设计。当 bash 命令返回非零退出码时，`BashErrorEnricher` 分析输出，生成可操作的提示——"npm install failed: try running with --legacy-peer-deps" 或 "Permission denied: ensure the file is not read-only"。这些提示不是工具结果的一部分（不占上下文预算），而是附加的元数据，帮助 Agent 理解错误并调整策略。

## 工具的相关性评分

56 个工具的 schema 占用前面提到的 11,000 token。一个自然的想法是：能不能只加载相关的工具？

`ToolRelevanceScorer` 尝试做这件事。它用 TF-IDF 风格的词项重叠来评分工具与查询的相关性：

```java
public static double score(ToolDefinition tool, String query,
                           List<ToolDefinition> allTools) {
    Set<String> queryTerms = tokenize(query);
    Map<String, Double> idfWeights = computeIdf(queryTerms, allTools);
    Set<String> toolTerms = tokenize(buildSearchText(tool));

    double weightedMatches = 0.0;
    double totalWeight = 0.0;
    for (String term : queryTerms) {
        double weight = idfWeights.getOrDefault(term, 1.0);
        totalWeight += weight;
        if (toolTerms.contains(term)) {
            weightedMatches += weight;
        }
    }
    return totalWeight > 0.0 ? weightedMatches / totalWeight : 0.0;
}
```

IDF 权重惩罚出现在多个工具中的常见词（如 "tool"、"file"、"the"），提升稀有的、有区分力的词。如果用户说 "search for error in Java files"，"search" 匹配 grep 和 glob，但 "error" 只匹配 grep——IDF 给 "error" 更高的权重，grep 得分更高。

但这个方案是静态的、启发式的。它不理解语义——"refactor" 和 "edit" 对它来说是两个毫不相关的词。它也不考虑上下文——在一个"调试编译错误"的会话中，lsp 工具应该始终可用，即使当前查询没有提到 "diagnostic"。

说实话，动态工具加载的问题我还没有解决。当前的方案是所有 56 个工具的 schema 始终存在于系统提示中。前面提到的 11,000 token 成本。这是一个已知的效率损失，但比错过一个关键工具的风险更可接受。

一个可能的方向是分层加载：核心工具（read、write、edit、bash、grep、glob）始终可用，其余工具按 session 类型或用户配置按需加载。但这需要解决一个 bootstrap 问题——模型在没有看到全部工具 schema 的情况下，怎么知道它需要某个工具？还在想。

## 工具设计的未竟之业

56 个工具是不是太多了？看从哪个角度。

从功能覆盖看，不够。我没有数据库查询工具、没有 Kubernetes 操作工具、没有 Terraform 工具、没有 Slack 发消息工具。每增加一个领域，就需要增加一批工具。如果要覆盖一个 SRE Agent 的全部需求，工具数量可能需要到 200+。

从上下文成本看，已经不少了。11,000 token 的工具 schema 是每次对话都要支付的固定税。在一个只需要读写文件的简单任务中，调度、团队协作、Cron 这些工具的 schema 纯粹是浪费。

从模型的选择能力看，56 个差不多到上限了。研究表明，当工具数量超过 50-80 个时，模型的工具选择准确率开始显著下降——尤其是语义接近的工具（`write` vs `batch_write`，`memory_write` vs `team_memory_write`）。

56 这个数字不是精心规划的结果。一路做加法，偶尔做减法，kairo-code 的场景写完后一数——56 个。如果未来模型的工具使用能力提升，这个数字会增长。如果动态加载方案成熟，固定成本会下降。但当下，56 是我们的平衡点。

---

*下一篇：《SPI 设计哲学——扩展性与极简主义的平衡》*

---

**参考**

1. VILA-Lab, "Dive into Claude Code: The Design Space of Today's and Future AI Agent Systems," arXiv:2604.14228, April 2026
2. Kairo ADR-010, Tool Result Budget Contract
3. Kairo 工具源码, `kairo-capabilities/kairo-tools/`
