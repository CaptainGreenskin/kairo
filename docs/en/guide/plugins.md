# Kairo Plugin System

> **Status:** Experimental — landed in v1.2. SPI may evolve in v1.3, stabilises in v1.4.

Kairo's plugin system reads the **same file format** Claude Code plugins use
(`plugin.json`, `SKILL.md`, `commands/*.md`, `agents/*.md`, `hooks/hooks.json`,
`.mcp.json`, `bin/`, `output-styles/`). This means a plugin authored once works in both
runtimes — you just rename the manifest directory.

## Migrating a Claude Code plugin

```bash
# Take any Claude Code plugin tree
git clone https://github.com/<owner>/<repo> my-plugin

# Rename .claude-plugin/ to .kairo-plugin/ — Kairo only reads its own namespace
mv my-plugin/.claude-plugin my-plugin/.kairo-plugin
```

That's the entire migration. Schema (plugin.json fields, skill frontmatter, hooks.json,
mcp.json) is shared. Variable names like `${CLAUDE_PLUGIN_ROOT}` inside a plugin's
config files are honored as compat aliases — you don't need to rewrite them.

## Plugin layout

```
my-plugin/
├── .kairo-plugin/
│   └── plugin.json           # required: name + version (exact MAJOR.MINOR.PATCH)
├── skills/
│   └── greet/SKILL.md        # markdown skills (frontmatter + body)
├── commands/                 # flat skills (each .md is one command)
│   └── quick-greet.md
├── agents/                   # subagent definitions (Phase D)
│   └── reviewer.md
├── hooks/
│   └── hooks.json            # event-driven hooks (command/http/prompt/agent/mcp_tool)
├── .mcp.json                 # MCP servers (stdio subprocess)
├── output-styles/            # response style overrides
├── bin/                      # executables; injected into agent shell PATH
└── themes/                   # (placeholder, v1.3+)
```

## plugin.json schema

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

**v1.2 enforces exact versions.** `^1.2.0`, `~1.2`, `1.x`, `latest` are rejected. The
`dependencies[]` field is recorded but **not yet resolved** — this arrives in v1.3.

## Five source types

A plugin can be installed from any of these sources:

| Source | Spec example |
|---|---|
| Local path | `/abs/path/to/plugin` or `./relative` |
| GitHub repo | `github:owner/repo` or `github:owner/repo@v1.0` |
| Any git URL | `git+https://example.com/repo.git` or `git+...@trunk` |
| Git monorepo subdir | `git-subdir+https://x.git@main:plugins/foo` |
| npm package | `npm:my-pkg@1.0.0` (or `npm:@scope/pkg@1.0.0`) |

Remote sources cache to `~/.kairo/plugins/cache/<type>/<sha8>/`. The second install
of the same source short-circuits.

## CLI (`kairo-assistant-cli`)

```text
/plugin list                       List installed plugins
/plugin install <source>           Install from any of the 5 source types
/plugin enable <id-prefix>         Activate a plugin (registers components)
/plugin disable <id-prefix>        Deactivate (keeps install on disk)
/plugin uninstall <id-prefix>      Remove entirely
/plugin update <id-prefix>         Re-load manifest from disk
```

`<id-prefix>` matches against either the plugin id or its name — most of the time the
plain name is enough.

## Programmatic API (`io.kairo.api.plugin.PluginManager`)

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

## Marketplace (`marketplace.json`)

A marketplace is a single JSON file in any git repo:

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

Parse it with `MarketplaceParser` and feed each entry's `source` into `PluginManager.install`.
Kairo does not run a marketplace server — distribution rides on git.

## Hook events

Hook events in `hooks.json` use Claude-Code-style names (`PreToolUse`, `SessionStart`,
…) which map to Kairo `HookPhase` values via `HookEventMapper`. The full mapping
table is in
[`kairo-capabilities/kairo-plugin/src/main/java/io/kairo/plugin/hook/HookEventMapper.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-plugin/src/main/java/io/kairo/plugin/hook/HookEventMapper.java).

Five action types are accepted: `command`, `http`, `prompt`, `agent`, `mcp_tool`.
v1.2 wires `command` and `http` to real execution; `prompt` / `agent` / `mcp_tool` are
parsed but evaluation is no-op until v1.3 wires them to the model and MCP runtimes.

## Migrating from the old `AssistantPlugin`

The pre-v1.2 `io.kairo.assistant.plugin.AssistantPlugin` interface and its
`PluginManager` were removed in v1.2 (incubation-stage breaking change — there were no
real implementations in the wild). Code that referenced them should switch to
`io.kairo.api.plugin.PluginManager` from `kairo-plugin`. See `AssistantAgentFactory.buildPluginManager`
for a reference wiring.

## See also

- [ADR-029 — Plugin SPI with Claude Code Format Compatibility](../../adr/ADR-029-plugin-spi-claude-code-compat.md)
- Source: `kairo-api/src/main/java/io/kairo/api/plugin/`,
  `kairo-capabilities/kairo-plugin/src/main/java/io/kairo/plugin/`
- Claude Code plugin reference (the format we read): <https://code.claude.com/docs/en/plugins-reference.md>
