# 插件兼容性

> **状态：** v1.2 基线。基于编写时 `anthropics/claude-code` 示例插件的 commit 进行验证。
> 随着新 schema 变体的引入持续更新。

本页记录了 Kairo 加载器所识别的 Claude Code 插件文件格式子集，并附有实证依据：
[`ClaudeCodeCompatTest`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-plugin/src/test/java/io/kairo/plugin/ClaudeCodeCompatTest.java)
TCK 测试套件包含了来自官方示例仓库的 5 个真实插件的原始副本，并针对它们运行加载器
进行验证。

## 已验证的插件

每行所代表的插件在加载时均未做任何源码修改，唯一操作是
`mv .claude-plugin .kairo-plugin`（且大多数插件甚至没有该目录 — 见"Manifest"列）。

| 插件 | 来源 | 组件 | Manifest | 结果 |
|---|---|---|---|---|
| `commit-commands` | claude-code/plugins | 2 个 commands | 无 | ✅ 全部解析成功 |
| `explanatory-output-style` | claude-code/plugins | 1 个 hook（SessionStart，command 类型） | 无 | ✅ `${CLAUDE_PLUGIN_ROOT}` 保持不变 |
| `frontend-design` | claude-code/plugins | 1 个 skill bundle（skills/frontend-design/SKILL.md） | 无 | ✅ |
| `pr-review-toolkit` | claude-code/plugins | 2 个 agents + 1 个 command | 无 | ✅ |
| `hookify` | claude-code/plugins | 4 个 hooks + 2 个 commands + 1 个 agent + 1 个 skill | 无 | ✅ 所有 timeout 和变量均保持不变 |

**加载器回归测试套件**：`mvn -pl kairo-plugin test -Dtest=ClaudeCodeCompatTest`。

## 加载器识别的内容

### `plugin.json` 字段

| 字段 | 状态 |
|---|---|
| `name` | ✅ 必需（若缺失则从目录名合成） |
| `version` | ✅ 必需，v1.2 中**仅接受精确版本**（不支持 `^1.0.0` / `~1.2` / `latest`） |
| `description` | ✅ |
| `author`（对象或字符串） | ✅ 两种形式均支持 |
| `license` | ✅ |
| `homepage` | ✅ |
| `keywords[]` | ✅ |
| `dependencies[]` | ⚠️ 已记录但在 v1.2 中**未进行解析** — v1.3 将添加解析器 |
| `mcpServers` | ✅ |

从 5 个真实插件的采样中得到的最重要发现：**它们全都没有 `plugin.json` 文件**。
Claude Code 运行时会从目录名合成 manifest，Kairo 的加载器也采用了相同策略 — 当
`.kairo-plugin/plugin.json` 不存在时，metadata 为 `(name=<dir-name>, version="0.0.0")`。

### 组件类型

| 目录结构 | 状态 | 备注 |
|---|---|---|
| `skills/<name>/SKILL.md` | ✅ | Frontmatter 由 `SkillRegistry.loadFromFile()` 解析 |
| `commands/*.md`（扁平技能） | ✅ | `allowed-tools:` frontmatter 已记录但未强制执行（v1.3） |
| `agents/*.md` | ✅ | `model:` 字段已存储；`color:` 字段被忽略（仅装饰用途） |
| `hooks/hooks.json` | ✅ | 全部 5 种 action 类型均可解析；`command`/`http` 可执行，其余为 stub |
| `.mcp.json` | ✅ | Stdio 服务器通过 kairo-mcp 启动 |
| `plugin.json#mcpServers` | ✅ | 内联 MCP，优先级高于 `.mcp.json` |
| `output-styles/*.md` | ⚠️ | 已加载到 `OutputStyleComponent`，但尚无运行时绑定 |
| `bin/*` | ✅ | 目录注入到 Agent shell PATH 中 |
| `themes/` | ⚠️ | 占位加载器；绑定推迟到 v1.3 |
| `monitors/` | ❌ | 尚未识别 |
| `.lsp.json` | ❌ | 尚未识别 |

### `hooks.json` Schema

| 元素 | 状态 |
|---|---|
| 顶层 `description` | ✅ 已记录 |
| `disableAllHooks: true` | ✅ 已生效 — 返回空 Hook 列表 |
| `hooks: { ... }` 下的按事件分组的数组 | ✅ |
| `matcher` 字段（字符串、`Bash\|Edit`、正则表达式） | ✅ 原样存储；matcher 求值由 Hook 执行器负责 |
| `hooks[].type` 类型判别器 | ✅ |
| `command` 动作 — `command`、`args`、`shell`、`timeout`、`env` | ✅ 可执行 |
| `http` 动作 — `url`、`headers`、`timeout` | ✅ 可执行 |
| `mcp_tool` 动作 — `server`、`tool`、`input` | ⚠️ 可解析，执行为 stub（Phase B.7 后续跟进） |
| `prompt` 动作 — `prompt`、`model`、`timeout` | ⚠️ 可解析，执行为 stub |
| `agent` 动作 — `prompt`、`model`、`timeout` | ⚠️ 可解析，执行为 stub |
| `${CLAUDE_PLUGIN_ROOT}` / `${KAIRO_PLUGIN_ROOT}` 变量 | ✅ 运行时均支持 |
| `${CLAUDE_PROJECT_DIR}` / `${KAIRO_PROJECT_DIR}` | ✅ |
| `${CLAUDE_PLUGIN_DATA}` / `${KAIRO_PLUGIN_DATA}` | ✅ |

### Hook 事件映射

29 个 Claude Code 风格的事件名称已映射到 Kairo 的 `HookPhase`。完整映射表参见
[`HookEventMapper.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-plugin/src/main/java/io/kairo/plugin/hook/HookEventMapper.java)。
值得注意的映射：

- `Stop` → `PRE_COMPLETE`（尽力匹配：Claude Code 的 `Stop` 在 Agent 主循环即将返回时
  触发；这与 Kairo 的 `PRE_COMPLETE` 语义最为接近）
- `Elicitation` / `ElicitationResult` → `NOTIFICATION`（Kairo 目前没有一级
  elicitation 阶段 — 尽力路由）
- 所有其他事件均为直接的一对一映射

## 迁移清单

将 Claude Code 插件迁移到 Kairo 的步骤：

1. 如果插件确实有 manifest 目录，执行 `mv .claude-plugin/ .kairo-plugin/`。
   （大多数插件没有 — 它们依赖目录名合成。）
2. 如果 `plugin.json` 使用了 `"version": "^1.0.0"` 或类似格式，改为精确版本号。
3. 完成。`${CLAUDE_PLUGIN_ROOT}` 等变量作为兼容别名继续生效 — 无需重写 Hook 脚本调用。

## v1.2 不涉及的范围

以下内容在实际使用中已出现，应在 v1.3 中考虑支持：

- **`agent`/`prompt`/`mcp_tool` Hook 执行。** 目前加载器可以正确解析这些类型，但
  `HookExecutor.runStubbed()` 返回空操作结果。将它们接入 ModelProvider + McpPlugin
  运行时是 v1.3 的优先事项。
- **`OutputStyle` 运行时绑定。** 已加载但尚未推入 Prompt 构建器。
- **`SubagentRegistry` 运行时调度。** 目前加载器会生成 `AgentComponent`，注册器也会
  捕获它，但父 Agent 对子 Agent 的实际调用尚未接入。
- **`monitors/` 和 `.lsp.json`** — 未识别。需要新增 `PluginComponent` 变体。
- **`commands/*.md` 的 `allowed-tools:` 强制执行** — 文件中已记录该字段，但权限规范
  尚未与 Kairo 的 `PermissionGuard` 做交集运算。

## 报告兼容性问题

如果某个实际的 Claude Code 插件无法在 Kairo 下正常加载，请按以下步骤操作：

1. 将插件的代表性文件（原样）添加到
   `kairo-capabilities/kairo-plugin/src/test/resources/fixtures/claude-code-samples/<plugin-name>/`
2. 在 `ClaudeCodeCompatTest` 中添加一个测试方法来复现该问题
3. 提交 Issue 或 PR — 该测试即作为回归记录

## 另请参阅

- [插件指南](plugins.md) — 面向用户的迁移和 CLI 参考
- [ADR-029](../../adr/ADR-029-plugin-spi-claude-code-compat.md) — 设计决策
- [`HookEventMapper.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-plugin/src/main/java/io/kairo/plugin/hook/HookEventMapper.java) — 事件名称兼容性的唯一真实来源
