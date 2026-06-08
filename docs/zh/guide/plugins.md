# Kairo 插件系统

> **状态：** Experimental（实验性） — 在 v1.2 中落地。SPI 可能在 v1.3 中演进，v1.4 趋于稳定。

Kairo 的 Plugin（插件）系统读取与 Claude Code 插件**完全相同的文件格式**
（`plugin.json`、`SKILL.md`、`commands/*.md`、`agents/*.md`、`hooks/hooks.json`、
`.mcp.json`、`bin/`、`output-styles/`）。这意味着一个插件只需编写一次，就能在两个
运行时中使用 — 只需重命名 manifest 目录即可。

## 从 Claude Code 插件迁移

```bash
# 获取任意 Claude Code 插件
git clone https://github.com/<owner>/<repo> my-plugin

# 将 .claude-plugin/ 重命名为 .kairo-plugin/ — Kairo 只读取自己的命名空间
mv my-plugin/.claude-plugin my-plugin/.kairo-plugin
```

这就是全部迁移工作。Schema（`plugin.json` 字段、skill frontmatter、`hooks.json`、
`mcp.json`）是共享的。插件配置文件中的 `${CLAUDE_PLUGIN_ROOT}` 等变量名会作为兼容别名
被识别 — 无需重写。

## 插件目录结构

```
my-plugin/
├── .kairo-plugin/
│   └── plugin.json           # 必需：name + version（精确的 MAJOR.MINOR.PATCH）
├── skills/
│   └── greet/SKILL.md        # Markdown 技能（frontmatter + 正文）
├── commands/                 # 扁平技能（每个 .md 文件对应一个命令）
│   └── quick-greet.md
├── agents/                   # Subagent（子智能体）定义（Phase D）
│   └── reviewer.md
├── hooks/
│   └── hooks.json            # 事件驱动的 Hook（钩子）（command/http/prompt/agent/mcp_tool）
├── .mcp.json                 # MCP 服务器（stdio 子进程）
├── output-styles/            # 响应样式覆盖
├── bin/                      # 可执行文件；注入到 Agent shell PATH 中
└── themes/                   # （占位，v1.3+）
```

## plugin.json Schema

```json
{
  "name": "my-plugin",
  "version": "1.0.0",
  "description": "What this plugin does",
  "author": { "name": "you", "email": "you@example.com" },
  "license": "MIT",
  "homepage": "https://...",
  "keywords": ["..."],
  "dependencies": [],
  "mcpServers": {
    "myserver": {
      "command": "${KAIRO_PLUGIN_ROOT}/bin/server",
      "args": ["--stdio"],
      "env": { "DEBUG": "1" }
    }
  }
}
```

**v1.2 强制要求精确版本号。** `^1.2.0`、`~1.2`、`1.x`、`latest` 均会被拒绝。
`dependencies[]` 字段会被记录，但**尚未进行依赖解析** — 依赖解析将在 v1.3 中实现。

## 五种来源类型

插件可以从以下任一来源安装：

| 来源类型 | 格式示例 |
|---|---|
| 本地路径 | `/abs/path/to/plugin` 或 `./relative` |
| GitHub 仓库 | `github:owner/repo` 或 `github:owner/repo@v1.0` |
| 任意 Git URL | `git+https://example.com/repo.git` 或 `git+...@trunk` |
| Git Monorepo 子目录 | `git-subdir+https://x.git@main:plugins/foo` |
| npm 包 | `npm:my-pkg@1.0.0`（或 `npm:@scope/pkg@1.0.0`） |

远程来源会缓存至 `~/.kairo/plugins/cache/<type>/<sha8>/`。同一来源的第二次安装会直接
命中缓存。

## CLI 命令（`kairo-assistant-cli`）

```text
/plugin list                       列出已安装的插件
/plugin install <source>           从 5 种来源类型中任一安装
/plugin enable <id-prefix>         激活插件（注册组件）
/plugin disable <id-prefix>        停用插件（保留磁盘上的安装）
/plugin uninstall <id-prefix>      完全移除
/plugin update <id-prefix>         从磁盘重新加载 manifest
```

`<id-prefix>` 可以匹配插件 ID 或插件名称 — 大多数情况下直接使用插件名称即可。

## 编程接口（`io.kairo.api.plugin.PluginManager`）

```java
PluginManager pm = new DefaultPluginManager(
    new DefaultPluginRegistry(),
    new PluginLoader(),
    Paths.get(System.getProperty("user.home"), ".kairo/plugins/data"),
    new KairoComponentRegistrar(skillRegistry, mcpRegistrar, pluginEnvironment),
    new SourceFetcherRegistry()
        .register(new LocalPathSourceFetcher())
        .register(new GitHubSourceFetcher(cache, http))
        .register(new GitUrlSourceFetcher(cache))
        .register(new GitSubdirSourceFetcher(cache))
        .register(new NpmSourceFetcher(cache, http)));

PluginInstallation inst = pm.install(
        new PluginSource.GitHub("owner/repo", "v1.0", null),
        PluginScope.PROJECT
).block();

pm.enable(inst.id()).block();
```

## Marketplace（插件市场）（`marketplace.json`）

Marketplace 是任意 Git 仓库中的一个 JSON 文件：

```json
{
  "name": "my-marketplace",
  "owner": { "name": "alice" },
  "trustLevel": "official",
  "plugins": [
    { "name": "skills-pack", "source": "./plugins/skills-pack" },
    { "name": "git-helpers", "source": { "github": "alice/git-helpers", "ref": "main" } },
    { "name": "weather",     "source": { "npm": "@alice/weather", "version": "1.2.3" } }
  ]
}
```

使用 `MarketplaceParser` 解析该文件，然后将每个条目的 `source` 传入 `PluginManager.install`。
Kairo 不运行 Marketplace 服务器 — 分发完全基于 Git。

## Hook 事件

`hooks.json` 中的 Hook（钩子）事件使用 Claude Code 风格的名称（`PreToolUse`、`SessionStart`
等），这些名称通过 `HookEventMapper` 映射到 Kairo 的 `HookPhase` 值。完整映射表参见
[`kairo-capabilities/kairo-plugin/src/main/java/io/kairo/plugin/hook/HookEventMapper.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-plugin/src/main/java/io/kairo/plugin/hook/HookEventMapper.java)。

支持五种 Action（动作）类型：`command`、`http`、`prompt`、`agent`、`mcp_tool`。
v1.2 中 `command` 和 `http` 已接入真实执行；`prompt` / `agent` / `mcp_tool` 可被正确
解析，但执行为空操作（no-op），直到 v1.3 将它们接入模型和 MCP 运行时。

## 从旧版 `AssistantPlugin` 迁移

v1.2 之前的 `io.kairo.assistant.plugin.AssistantPlugin` 接口及其 `PluginManager` 已在
v1.2 中被移除（孵化阶段的破坏性变更 — 当时没有实际的外部实现）。引用了这些接口的代码
应改为使用 `kairo-plugin` 模块中的 `io.kairo.api.plugin.PluginManager`。参考接线方式
见 `AssistantAgentFactory.buildPluginManager`。

## 另请参阅

- [ADR-029 — 兼容 Claude Code 格式的 Plugin SPI](../../adr/ADR-029-plugin-spi-claude-code-compat.md)
- 源码：`kairo-api/src/main/java/io/kairo/api/plugin/`、
  `kairo-capabilities/kairo-plugin/src/main/java/io/kairo/plugin/`
- Claude Code 插件参考（Kairo 所读取的格式）：<https://code.claude.com/docs/en/plugins-reference.md>
