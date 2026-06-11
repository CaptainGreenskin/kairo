# Kairo + kairo-code Roadmap: M57 – M60

> **Status**: M57 ✅ CLOSED | M58 ✅ CLOSED | M59 ✅ CLOSED | M60 ✅ CLOSED  
> **Scope**: Kairo 框架层 tool/skill 封口 + kairo-code CLI 产品完善  
> **Principle**: Kairo 完善优先，kairo-code 是其上的产品验证（dogfood）

---

## Milestone Overview

```
M57 (Tool Layer 封口)        ── 2 weeks ── Kairo framework
M58 (Skill 系统完整实现)     ── 3 weeks ── Kairo framework + kairo-code CLI
M59 (Memory 系统)            ── 3 weeks ── kairo-code core
M60 (CLI REPL 封口)          ── 4 weeks ── kairo-code CLI
```

### Dependencies

```
M57 ✅ CLOSED ──────────── M58 Kairo layer (测试补充)
                              │
                           M58 kairo-code CLI layer (S-06, S-10, S-11) ⭐ 主要交付
                              │
M58 完成 ─────────────── M59 Option A/B 决策签发
                              │
M59 ──────────────────── M60
```

- M57 ✅ CLOSED — all gates satisfied (502 tests green)
- **M58 Kairo layer 现为测试补充（非 S-04 新交付）**，CLI layer 为主要交付
- **M59 Option A/B 决策必须在 M58 完成时签发**（决策不再依赖 S-04 交付，因 Skill SPI 已 @Stable）
- M59 and M60 can overlap once M58 CLI layer is stable

---

## M57 — Tool Layer 封口（目标 2 周）

> Kairo 框架层 — 补齐工具测试覆盖

> **Status: ✅ M57 CLOSED — Wave 1 已满足 (87 tests) | Wave 2 已关闭 (TeamCreateToolTest 20 cases, 502 total tests green)**

### Wave 1: kairo-code 核心工具（优先） — ✅ SATISFIED

| #    | 任务                        | 模块        | 目标类                                             |
|------|-----------------------------|-------------|----------------------------------------------------|
| T-01 | GlobTool 语义对齐测试       | kairo-tools | GlobToolTest (10+ 用例：glob 模式、排除目录、排序) |
| T-02 | GrepTool 上下文行数测试     | kairo-tools | GrepToolTest (10+ 用例：-C/-A/-B, 多文件, 正则)   |
| T-10 | BashTool 安全测试           | kairo-tools | BashToolSecurityTest (注入防护、超时、沙箱切换)    |
| T-14a | EditTool 测试 | kairo-tools | EditToolTest (10+ 用例：行编辑、范围替换、并发写) |
| T-14b | SearchReplaceTool 测试 | kairo-tools | SearchReplaceToolTest (10+ 用例：正则替换、多文件、dry-run) |
| T-16 | ReadTool 综合测试 | kairo-tools | ReadToolTest (10+ 用例：路径解析、编码、大文件、权限) |
| T-17 | WriteTool 综合测试 | kairo-tools | WriteToolTest (10+ 用例：创建、覆盖、追加、目录创建、权限) |

> **Rationale**: kairo-code M1 已使用 BashTool、ReadTool、WriteTool、EditTool、GrepTool、GlobTool 六个工具。Wave 1 优先覆盖这些核心工具的测试缺口

> **Wave 1 已满足**: 所有 7 个核心工具已有完整测试覆盖（共 87 个测试，全部通过），无需新增测试。
>
> - GlobTool: 10 tests ✅
> - GrepTool: 14 tests ✅
> - BashTool: 17 tests ✅ (includes security/injection tests)
> - EditTool: 10 tests ✅
> - SearchReplaceTool: 13 tests ✅
> - ReadTool: 12 tests ✅
> - WriteTool: 11 tests ✅

### Wave 2: 完整覆盖

| #    | 任务                                       | 模块        | 目标类                                          |
|------|--------------------------------------------|-------------|-------------------------------------------------|
| T-03 | PatchApplyTool 测试                        | kairo-tools | PatchApplyToolTest (unified diff 应用、冲突检测) |
| T-04 | TemplateRenderTool 测试                    | kairo-tools | TemplateRenderToolTest (Mustache/变量插值)       |
| T-05 | WebFetchTool 综合测试                      | kairo-tools | WebFetchToolTest (mock HTTP, 超时, SSRF guard)   |
| T-06 | WebSearchTool 测试                         | kairo-tools | WebSearchToolTest (mock 搜索结果解析)            |
| T-07 | GithubTool 测试                            | kairo-tools | GithubToolTest (PR/issue/branch CRUD mock)       |
| T-08 | MvnTool 测试                               | kairo-tools | MvnToolTest (命令构建、超时、沙箱)               |
| T-09 | ~~MonitorTool 附加测试~~                 | kairo-tools | **已跳过** — MonitorToolTest 已存在；MonitorTool 功能可用 BashTool 代替，非 Kairo 差异化能力 |
| T-12 | ~~DockerSandbox 集成测试~~               | kairo-tools | **已跳过** — optional，需要 docker daemon + CI profile `-Pdocker` |
| T-13 | OpenApiHttpTool 综合测试                   | kairo-tools | OpenApiHttpToolTest (schema 解析、参数映射)      |
| T-15 | AgentSpawnTool / TeamCreateTool 测试       | kairo-tools | AgentSpawnToolTest, TeamCreateToolTest — **进行中** — 多 Agent 团队协调是 Kairo 相对 Claude Code 的核心差异化，唯一剩余项 |

### M57 Gate
- [x] Wave 1: 7 个核心 tool 测试类 green，每个 ≥10 用例 — **SATISFIED** (87 tests total)
- [x] Wave 2: TeamCreateToolTest 通过 (20 cases)，T-09 已跳过，T-12 已跳过 — **CLOSED**
- [x] `mvn verify -pl kairo-tools` 全绿 (502 total tests green)
- [x] 零 P0/P1 遗留

---

## M58 — Skill 系统完整实现（目标 3 周）

### Kairo 框架层（测试补充）

| #    | 任务                   | 说明                                                                       |
|------|------------------------|----------------------------------------------------------------------------|
| S-01 | SkillRegistry SPI 完善 | kairo-skill 模块：优先级排序（user > project > classpath > bundled），热重载 debounce 500ms |
| S-02 | Skill 参数插值引擎     | variable → `${args[0]}` 映射；支持 `$env.VAR`                             |
| S-03 | ~~Skill 条件激活完善~~ | **❌ 已删除** — Instructions-only 是正确的设计。Skill 本质是"告诉 LLM 怎么做"，Agent 本身就是 executor。skill 内容注入 system prompt，模型读了就执行，循环已闭合。没有具体场景不建 SPI。 |
| S-04 | SkillRegistry 集成测试 | SkillRegistryIT — 热重载端到端                                             |
| S-04b | SkillListTool / SkillLoadTool / SkillManageTool 测试 | 各自 *Test (10+ 用例)；depends on S-01 (SkillRegistry SPI 稳定后才能测试 Skill 工具) |

**M58 Kairo Layer 范围调整（测试补充 only）**:
- SkillHotReloadWatcherTest: 6 → 10+ tests
- SkillContentInjectorTest: 4 → 10+ tests
- SkillListToolTest: 7 → 10+ tests
- SkillLoadToolTest: 9 → 10+ tests

S-01 (SkillRegistry SPI) 已存在且 @Stable (v0.5+)，无需新增。
S-02 (SkillDefinition Schema) 已存在且完整 (12 fields)，无需新增。
S-03 已删除。
S-04b (Skill Tool Tests) 已达标 (30 tests)，无需新增。

### kairo-code CLI 层（⭐ M58 主要交付）

| #    | 任务                    | 说明                                                                              |
|------|-------------------------|-----------------------------------------------------------------------------------|
| S-06 | /skill 命令完整实现     | 目前 SkillCommand 存在，需 wire 到 FsSkillLoader + 显示 skill 列表/执行 skill     |
| S-09 | 内置 /compact skill     | 手动触发上下文压缩 (CompactCommand 已存在，确认 wire 到 ContextCompactionEngine)   |
| S-10 | Skill tab 补全          | REPL 输入 / 时触发 JLine completer，列出已注册 skill 名                           |
| S-11 | .kairo/skills/ 目录扫描 | FsSkillLoader 支持 `~/.kairo/skills/`（全局）和 `./.kairo/skills/`（项目级）双路径 |

### Regrouping Notes

以下任务从原 M58 移出：

| 原编号 | 任务               | 移至 | 理由                                |
|--------|--------------------|------|-------------------------------------|
| S-05   | ReplLoop shell 透传 `!<cmd>` | M60  | 非 Skill 功能，属 REPL 交互增强     |
| S-07   | kairo-code init 命令          | M60  | 项目脚手架功能，属 CLI 完善          |
| S-08   | 内置 /memory skill            | M59  | Memory 系统子功能，M-07 已覆盖       |

### M58 Gate — ✅ ALL SATISFIED (2026-06-10)
- [x] Kairo layer: 4 个 Skill 测试类各 ≥10 用例 (HotReload=11, ContentInjector=10, ListTool=11, LoadTool=11)
- [x] CLI layer: S-06 `/skill` 命令功能可用 (SkillCommand 236 LOC)
- [x] CLI layer: S-10 tab 补全可用 (ReplCompleter 集成 SkillRegistry)
- [x] CLI layer: S-11 双路径扫描可用 (FsSkillLoader: ~/.kairo-code/skills/ + .kairo-code/skills/)
- [x] `mvn verify` 全绿 (kairo-skill: 159 tests, BUILD SUCCESS)
- [x] All tests green

### M59 Decision Log
- **2026-06-10**: FileMemoryStore 决策 → **Option A**（在 Kairo 框架层实现 FileMemoryStore，kairo-code 消费它）。理由：符合 dogfood 原则，框架层受益。

---

## M59 — Memory / Self-Evolution 系统（目标 3 周）

> kairo-code core 层

| #    | 任务                    | 说明                                                                                                       |
|------|-------------------------|------------------------------------------------------------------------------------------------------------|
| M-01 | MemoryFile 格式标准化   | frontmatter: type/name/description + body；支持 user/feedback/project/reference 四种类型                   |
| M-02 | 自动记忆触发器          | AutoMemoryHook — @OnSessionEnd：检测用户偏好、反馈信号，自动写入 `.kairo/memory/`                          |
| M-03 | MEMORY.md 索引          | MemoryIndexWriter — 每次写入 memory 文件后更新 MEMORY.md 的 index 条目（≤200 行限制）                      |
| M-04 | 会话启动加载 memory     | SessionContextEnricher — 读取 MEMORY.md + 相关 .md 文件，注入 system prompt section                       |
| M-05 | 记忆 LRU 过期策略       | MemoryGarbageCollector — 按 last_accessed 时间戳，超过 30 天未读的 **project** 类型自动归档；**feedback** 和 **reference** 类型永不自动过期（仅允许用户手动删除）。Rationale: feedback 是用户对 agent 行为的明确矫正，自动过期会遗忘已学到的约束；reference 是外部资源指针，不应自动消失 |
| M-06 | 跨会话 memory 加载测试  | MemoryLoadIntegrationTest — 写入 → 重启 REPL → 验证 system prompt 含对应 memory                           |
| M-07 | /memory 命令 Full CRUD  | MemoryCommand：/memory save/list/delete/edit/search（上接原 S-08，扩展 edit/search 子命令）                |

### Hard Gate: FileMemoryStore 决策

> **kairo-code Memory 与 Kairo MemoryStore SPI 的关系?**
>
> Kairo 已有 `MemoryStore` SPI（InMemory、JDBC、compaction）。kairo-code 的 `.kairo/memory/` 文件系统方案有两条路：
>
> - **Option A**: 新增 `FileMemoryStore` 实现 Kairo 的 `MemoryStore` SPI → 框架层受益，但需要先在 Kairo 中实现
> - **Option B**: kairo-code 独立实现文件 Memory，不走 Kairo SPI → 快速交付，但不回馈框架
>
> **建议**: Option A（先在 Kairo 加 `FileMemoryStore`，kairo-code 消费它）。这符合 dogfood 原则。
>
> **⚠ 决策截止点**: M58 完成前必须 resolve（Kairo Skill SPI 已 @Stable，决策不再依赖 S-04 交付）。  
> 若选 Option A，需在 M58 期间同步启动 Kairo 侧 `FileMemoryStore` 设计+实现，否则 M59 第一周即被阻塞。  
> **Action**: M58 完成时，reviewer 签发 Option A 或 Option B，写入本文档 Decision Log。

### M59 Gate
- [ ] Memory CRUD 端到端（write → read → update → delete）
- [ ] 跨会话持久化验证
- [ ] MEMORY.md 索引准确且 ≤200 行
- [ ] LRU 归档仅影响 project 类型；user/feedback/reference 类型不被自动归档
- [ ] `/memory` 命令全子命令可用

---

## M60 — kairo-code CLI REPL 封口（目标 4 周）

| #    | 任务                         | 说明                                                                                        |
|------|------------------------------|---------------------------------------------------------------------------------------------|
| R-01 | 会话持久化                   | SessionPersistence — 每轮对话追加写入 `.kairo/sessions/<ts>.jsonl`；支持 `/resume <session-id>` 恢复 |
| R-02 | 上下文窗口 UI                | REPL prompt 下方显示 `[tokens: 12k/200k \| compact: Snip]`；token 超 80% 时变色警告        |
| R-03 | 多行输入支持                 | JLine `\` 续行；或 `<<EOF` heredoc 模式（遇到 `<<EOF` 开头的行切换多行编辑模式，直到输入 `EOF` 行结束） |
| R-04 | Shell 透传 `!<cmd>`          | 输入以 `!` 开头 → 交给 ProcessBuilder 执行，stdout 直接打印到终端（原 S-05）                |
| R-05 | `kairo-code init` 命令       | 初始化 `.kairo/` 目录，写 KAIRO.md 模板，注册 MCP servers（原 S-07）                        |
| R-06 | `/doctor` 诊断命令           | 检查 API key、model 连通性、Kairo BOM 版本、.kairo/ 目录结构 — 一键排障                     |
| R-07 | REPL E2E 测试补全            | 补齐 M1 5 个 E2E 之外的场景：multi-line、session resume、shell 透传、skill tab 补全         |

> **Note**: R-04 和 R-05 从 M58 移入（非 Skill 功能）。R-06 为新增诊断命令。

### Relationship to Original AgentCode Plan

| 原 AgentCode 计划 | M60 对应 | 说明                                           |
|--------------------|----------|------------------------------------------------|
| M2 T13 `:snapshot`/`:resume` | R-01     | R-01 supersedes — JSONL 格式更实用              |
| M2 T12 `:skill load`         | M58 S-06 | M58 supersedes — 更完整的 Skill 系统            |
| M2 T11 `:plan` mode          | _deferred_ | 需评估 Kairo Plan SPI 就绪度后再定             |
| M2 T14 error recovery        | _carried_ | 延续到 M60，融入 session persistence            |

### M60 Gate
- [ ] 会话 resume 跨进程重启可用
- [ ] 上下文窗口 UI 显示正确，80% 警告触发
- [ ] 多行输入支持 `\` 和 `<<EOF` 两种模式
- [ ] Shell 透传 `!ls` 等常见命令正常
- [ ] `/doctor` 检测 API key + model + .kairo/ 结构
- [ ] E2E 测试 ≥12 个场景 green

---

## Kairo SPI Feedback Log (Dogfood)

从 kairo-code M0-M1 已发现的 Kairo 框架问题：

| # | Issue | Severity | Status |
|---|-------|----------|--------|
| 1 | Agent 接口无 streaming API (`Flux<AgentEvent> callStream(Msg)`) | P1 | Reported — kairo-code 用 Hook workaround |
| 2 | ConsoleApprovalHandler 不支持取消（stdin reader 泄漏） | P0 | kairo-code 侧已修复；Kairo core 侧待修 |
| 3 | _(M57-M60 执行中将持续补充)_ | | |

---

## Risk Mitigations

| Risk | Mitigation |
|------|------------|
| M57 scope 过大（15 tools）| Wave 分拆：Wave 1 优先 kairo-code 核心 6 tools |
| Skill 热重载不稳定 | S-04 集成测试必须覆盖 create/update/delete 三种 FS 事件 |
| Memory 文件系统 vs Kairo SPI 选择 | Option A/B 决策在 M58 完成前 resolve |
| M60 scope creep | 明确 anti-goal：不做 IDE 集成、不做 Web UI（留 M4） |
| Kairo SPI 缺口阻塞 kairo-code | 发现即上报，Kairo 修复优先级高于 kairo-code 功能开发 |

---

## Timeline (Estimated)

```
M57 ████████░░░░░░░░░░░░░░░░░░░░░░░░  Week 1-2
M58 ░░░░░░░░████████████░░░░░░░░░░░░  Week 2-5  (Kairo layer starts Week 2; CLI layer starts Week 4)
M59 ░░░░░░░░░░░░░░░░████████████░░░░  Week 5-8
M60 ░░░░░░░░░░░░░░░░░░░░████████████  Week 8-12
```

> Wave 1 of M57 and M58 Kairo layer can overlap starting Week 2.
> M59 and M60 can partially overlap once M58 Kairo layer stabilizes.
