# Plugin Compatibility Matrix

> **Status:** v1.2 baseline. Verified against `anthropics/claude-code` demo plugins
> commit at the time of writing. Updated as new schema variants land.

This page records what subset of the Claude Code plugin file format Kairo's loader
recognises, with empirical evidence: the
[`ClaudeCodeCompatTest`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-plugin/src/test/java/io/kairo/plugin/ClaudeCodeCompatTest.java)
TCK ships verbatim copies of 5 real plugins from the official demo repo and exercises
the loader against them.

## Verified plugins

Each row was loaded with no source modification beyond
`mv .claude-plugin .kairo-plugin` (and many of these don't even have that directory
— see "no manifest" column).

| Plugin | Source | Components | Manifest | Result |
|---|---|---|---|---|
| `commit-commands` | claude-code/plugins | 2 commands | none | ✅ all parsed |
| `explanatory-output-style` | claude-code/plugins | 1 hook (SessionStart, command type) | none | ✅ `${CLAUDE_PLUGIN_ROOT}` preserved |
| `frontend-design` | claude-code/plugins | 1 skill bundle (skills/frontend-design/SKILL.md) | none | ✅ |
| `pr-review-toolkit` | claude-code/plugins | 2 agents + 1 command | none | ✅ |
| `hookify` | claude-code/plugins | 4 hooks + 2 commands + 1 agent + 1 skill | none | ✅ all timeouts/variables preserved |

**Loader regression suite**: `mvn -pl kairo-plugin test -Dtest=ClaudeCodeCompatTest`.

## What the loader recognises

### `plugin.json` fields

| Field | Status |
|---|---|
| `name` | ✅ required (or synthesised from directory name) |
| `version` | ✅ required, **exact form only** in v1.2 (no `^1.0.0` / `~1.2` / `latest`) |
| `description` | ✅ |
| `author` (object or string) | ✅ both shapes |
| `license` | ✅ |
| `homepage` | ✅ |
| `keywords[]` | ✅ |
| `dependencies[]` | ⚠️ recorded but **not resolved** in v1.2 — v1.3 adds resolver |
| `mcpServers` | ✅ |

The most important finding from sampling 5 real plugins: **none of them ship a
`plugin.json` at all**. The Claude Code runtime synthesises the manifest from the
directory name, and Kairo's loader does the same — when no `.kairo-plugin/plugin.json`
exists, metadata is `(name=<dir-name>, version="0.0.0")`.

### Component types

| Layout | Status | Notes |
|---|---|---|
| `skills/<name>/SKILL.md` | ✅ | Frontmatter parsed by `SkillRegistry.loadFromFile()` |
| `commands/*.md` (flat skill) | ✅ | `allowed-tools:` frontmatter recorded but not enforced (v1.3) |
| `agents/*.md` | ✅ | `model:` field stored; `color:` field ignored (cosmetic) |
| `hooks/hooks.json` | ✅ | All 5 action types parsed; `command`/`http` execute, others stubbed |
| `.mcp.json` | ✅ | Stdio servers start via kairo-mcp |
| `plugin.json#mcpServers` | ✅ | Inline MCP, takes precedence over `.mcp.json` |
| `output-styles/*.md` | ⚠️ | Loaded into `OutputStyleComponent` but no runtime binding yet |
| `bin/*` | ✅ | Directory injected into agent shell PATH |
| `themes/` | ⚠️ | Placeholder loader; binding deferred to v1.3 |
| `monitors/` | ❌ | Not yet recognised |
| `.lsp.json` | ❌ | Not yet recognised |

### `hooks.json` schema

| Element | Status |
|---|---|
| Top-level `description` | ✅ recorded |
| `disableAllHooks: true` | ✅ honored — returns empty hook list |
| Per-event arrays under `hooks: { ... }` | ✅ |
| `matcher` field (string, `Bash\|Edit`, regex) | ✅ stored verbatim; matcher evaluation is hook executor's job |
| `hooks[].type` discriminator | ✅ |
| `command` action — `command`, `args`, `shell`, `timeout`, `env` | ✅ executes |
| `http` action — `url`, `headers`, `timeout` | ✅ executes |
| `mcp_tool` action — `server`, `tool`, `input` | ⚠️ parsed, execution is stub (Phase B.7 follow-up) |
| `prompt` action — `prompt`, `model`, `timeout` | ⚠️ parsed, execution is stub |
| `agent` action — `prompt`, `model`, `timeout` | ⚠️ parsed, execution is stub |
| `${CLAUDE_PLUGIN_ROOT}` / `${KAIRO_PLUGIN_ROOT}` variables | ✅ both honored at runtime |
| `${CLAUDE_PROJECT_DIR}` / `${KAIRO_PROJECT_DIR}` | ✅ |
| `${CLAUDE_PLUGIN_DATA}` / `${KAIRO_PLUGIN_DATA}` | ✅ |

### Hook event mapping

29 Claude-Code-style event names mapped to Kairo's `HookPhase`. See
[`HookEventMapper.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-plugin/src/main/java/io/kairo/plugin/hook/HookEventMapper.java)
for the full table. Notable:

- `Stop` → `PRE_COMPLETE` (best-effort: Claude Code's `Stop` fires when the agent's
  main loop is about to return; this matches Kairo's `PRE_COMPLETE` semantics most
  closely)
- `Elicitation` / `ElicitationResult` → `NOTIFICATION` (Kairo has no first-class
  elicitation phase yet — best-effort routing)
- All other events have direct 1:1 mappings

## Migration checklist

To migrate a Claude Code plugin to Kairo:

1. `mv .claude-plugin/ .kairo-plugin/` if the plugin actually has a manifest directory.
   (Most don't — they rely on directory-name synthesis.)
2. If `plugin.json` uses `"version": "^1.0.0"` or similar, change to an exact version.
3. Done. `${CLAUDE_PLUGIN_ROOT}` and friends keep working as compat aliases — no need
   to rewrite hook script invocations.

## Out of scope for v1.2

These appear in the wild and should be considered for v1.3:

- **`agent`/`prompt`/`mcp_tool` hook execution.** Currently the loader parses these
  cleanly but `HookExecutor.runStubbed()` returns a no-op result. Wiring them to the
  ModelProvider + McpPlugin runtimes is the v1.3 priority.
- **`OutputStyle` runtime binding.** Loaded but not yet pushed into the prompt builder.
- **`SubagentRegistry` runtime dispatch.** Currently the loader emits an
  `AgentComponent` and the registrar captures it, but actual subagent invocation by
  parent agents is not yet wired.
- **`monitors/` and `.lsp.json`** — not recognised. Would need new `PluginComponent`
  variants.
- **`commands/*.md` `allowed-tools:` enforcement** — recorded in the file but the
  permission spec isn't intersected with kairo's `PermissionGuard` yet.

## Reporting compat issues

If a real-world Claude Code plugin doesn't load cleanly under Kairo, please:

1. Add the plugin's representative files (verbatim) under
   `kairo-capabilities/kairo-plugin/src/test/resources/fixtures/claude-code-samples/<plugin-name>/`
2. Add a test method to `ClaudeCodeCompatTest` reproducing the failure
3. Open an issue or PR — the test serves as the regression record

## See also

- [Plugin guide](plugins.md) — user-facing migration and CLI reference
- [ADR-029](../../adr/ADR-029-plugin-spi-claude-code-compat.md) — design decision
- [`HookEventMapper.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-plugin/src/main/java/io/kairo/plugin/hook/HookEventMapper.java) — single source of truth for event-name compat
