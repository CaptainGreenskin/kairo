# ADR-029 — Plugin SPI with Claude Code Format Compatibility (v1.2)

## Status

Proposed — implementation lands in v1.2 (2026-05). Introduces `io.kairo.api.plugin.*` SPI
and the `kairo-plugin` capability module. Marked `@Experimental` until v1.4.

Cross-references: [ADR-019 hook API consolidation](ADR-019-hook-api-consolidation.md),
[ADR-021 channel SPI](ADR-021-channel-spi.md), [ADR-023 SPI stability policy](ADR-023-spi-stability-policy.md).

## Context

Kairo through v1.1 has no first-class plugin system. The dogfood application
`kairo-assistant` carries a placeholder `AssistantPlugin` interface (two methods —
`onLoad`, `onUnload`) wired through Java's `ServiceLoader`. It has zero real-world
implementations: in production the registry returns an empty list. Users wanting to
extend Kairo today must fork-and-build or wire their tools/skills as Spring beans.

Meanwhile Claude Code has reached an industrial-strength plugin ecosystem — 35+ official
plugins, five source types (path / GitHub / arbitrary git URL / git subdirectory / npm),
30+ hook events, marketplace catalogs, and a well-defined declarative file format
(`plugin.json`, `SKILL.md`, `hooks.json`, `.mcp.json`). The format is portable: nothing
in the schema depends on the JS/TS runtime. A loader that reads it can run in any
language.

The strategic question is not "should Kairo have a plugin system" but "should it ship
its own format or adopt the format that already has a generation of plugins behind it."
The latter is dramatically cheaper for ecosystem reach, but only if Kairo can read the
format faithfully without becoming a Claude Code clone.

## Decision

Introduce `io.kairo.api.plugin.*` as a new `@Experimental` SPI surface, and a new
capability module `kairo-plugin` that implements the loader on top of it. The loader
parses the Claude Code plugin **file schema** verbatim (same `plugin.json` shape, same
`SKILL.md` frontmatter, same `hooks.json` structure, same `.mcp.json` keys) but lives
under Kairo-native package and class names and uses Kairo-native runtime infrastructure
(skill registry, hook chain, MCP plugin SPI, etc.) for component dispatch.

### Five non-negotiables

1. **Format compatibility, not directory compatibility.** Kairo plugins live under
   `.kairo-plugin/`. To migrate a Claude Code plugin, the user renames the directory.
   The schema inside is unchanged. This makes the namespace ours; the content is shared.

2. **Plugin SPI sits next to existing SPIs, never replaces them.** A plugin is a
   *cross-cutting aggregate* that contributes to existing registries (`SkillRegistry`,
   `HookChain`, `McpPlugin`, `PluginEnvironment` for `bin/`, etc.). It does not define a
   parallel registration surface.

3. **Atomicity.** Component registration during plugin enable is sequenced
   (tools → skills → agents → hooks → mcp → bin → outputStyles → themes); any step
   failing rolls back every previously-registered sibling for that plugin.

4. **Variable name compatibility is preserved.** `${KAIRO_PLUGIN_ROOT}` is canonical;
   `${CLAUDE_PLUGIN_ROOT}` (and the `_DATA` / `_PROJECT_DIR` siblings) are honored as
   compat aliases — not because we want a Claude Code namespace in Kairo, but because
   plugin files in the wild hard-code those strings inside `.mcp.json` / `hooks.json`.

5. **Marketplace = git, not a Kairo-hosted server.** A marketplace is just a
   `marketplace.json` file in a git repository. Kairo provides the parser and source
   fetchers; distribution rides on existing git infrastructure. This bounds the project's
   scope and avoids the AGENTCODE anti-goal of running plugin marketplaces.

### Source types (5)

Each is a sealed `PluginSource` variant with a matching `PluginSourceFetcher`
implementation in `io.kairo.plugin.source.*`:

| Variant | Resolution |
|---|---|
| `LocalPath` | Filesystem path, used as-is (no copy) |
| `GitHub` | `https://github.com/<owner/repo>/archive/<ref\|sha>.tar.gz` (no API token) |
| `GitUrl` | JGit shallow clone (`depth=1`) of any git URL |
| `GitSubdir` | JGit shallow clone, return resolved subdirectory |
| `Npm` | `https://registry.npmjs.org/<pkg>` metadata + tarball + SHA-1 verify |

All remote fetchers cache to `~/.kairo/plugins/cache/<type>/<sha8>/`, and the second
fetch with the same identity short-circuits on cache hit.

### Component contributions

| Source file | Contribution → Kairo registry |
|---|---|
| `skills/<name>/SKILL.md` | `SkillRegistry.loadFromFile()` |
| `commands/*.md` (flat skill) | `SkillRegistry.loadFromFile()` (same path) |
| `agents/*.md` | `SubagentRegistry.register()` *(new SPI in `io.kairo.api.agent.*`)* |
| `hooks/hooks.json` | `HookChain.register()` via `HookEventMapper` + `HookExecutor` |
| `.mcp.json` / `plugin.json#mcpServers` | `McpPlugin.register(McpServerConfig)` (kairo-mcp stdio) |
| `bin/*` | `PluginEnvironment.addBinDir()` (PATH augmentation for spawned subprocesses) |
| `output-styles/*.md` | (deferred — placeholder ComponentLoader) |
| `themes/*.md` | (deferred — placeholder ComponentLoader) |

### Hook event vocabulary

Two name spaces are accepted by `HookEventMapper.toPhase(eventName)`:

- Claude-Code-compatible names (`PreToolUse`, `SessionStart`, `Stop`, …) — what plugin
  files in the wild use; matches via a single-source-of-truth map.
- Kairo enum constants (`PRE_ACTING`, `SESSION_START`, …) — for native plugins authored
  inside Kairo's idiom.

The full table is documented in `HookEventMapper.java` so future schema drift is
auditable in one place.

### Stability and lifecycle

All `io.kairo.api.plugin.*` types ship as `@Experimental("Plugin SPI — contract may
change in v1.x")`. Per [ADR-023](ADR-023-spi-stability-policy.md) this means breaking
changes are allowed across minor versions. The plan is to graduate the SPI to `@Stable`
in v1.4, after at least one minor of real-plugin usage has shaken out the schema.

## Consequences

### Positive

- The full body of existing Claude Code plugins is reachable without porting — a user
  with a Claude Code plugin tree only renames `.claude-plugin/` to `.kairo-plugin/`
  before installing.
- Each component contribution path uses an existing Kairo SPI (no duplication). Future
  framework improvements to skills/hooks/MCP automatically apply to plugin-contributed
  resources.
- Marketplace = git eliminates an entire class of operational concerns (server uptime,
  index moderation, search). Plugin authors publish exactly the way they already do.

### Negative

- We carry the burden of tracking the Claude Code schema as it evolves. Mitigation:
  schema lives in clearly-named files (`HookEventMapper`, `MarketplaceParser`,
  `PluginManifestParser`); changes land as additive map entries, never as silent
  behavior shifts.
- Variable name aliases (`${CLAUDE_*}`) are a small permanent compat surface. Acceptable
  cost given the alternative (forcing every plugin to be re-edited before use).
- Adds two transitive dependencies to `kairo-plugin`: `commons-compress` (tar/gzip) and
  `org.eclipse.jgit` (git transport). Both are widely deployed and well-maintained.

### Anti-goal disambiguation

The `.plans/AGENTCODE-PROJECT-PLAN.md` lists "no plugin marketplace" as an anti-goal of
the AgentCode application. That anti-goal applies to **operating** a marketplace
service. ADR-029 does not contradict it: Kairo provides the **parser** and the
**fetchers** for a marketplace.json file. Distribution rides on git. No Kairo server
hosts a plugin index, hosts plugin bytes, or runs plugin verification. The marketplace
file format is just another schema we know how to read.

## Out of scope (for v1.2)

- **CLI commands.** `kairo plugin install/list/enable/...` is wired into the
  `kairo-assistant-cli` REPL only (the dogfood); a standalone CLI tool is a v1.3+ topic.
- **Java-native plugins.** The `@PluginContribution` annotation is reserved in `kairo-api`
  but not yet active. v1.3 will add a discovery path so in-process Java code can act as
  a plugin without a directory layout.
- **Dependency resolution between plugins.** v1.2 records `plugin.json#dependencies`
  but does not resolve transitively or order startup. v1.3 introduces `DependencyResolver`.
- **Sparse-checkout for `GitSubdir`.** Currently does a full shallow clone and resolves
  the subdirectory afterward. JGit doesn't expose first-class sparse-checkout cleanly;
  optimisation deferred until storage cost matters.
- **Per-plugin MCP unregister.** kairo-mcp's `McpPlugin` SPI doesn't expose a per-server
  release method yet. `PluginMcpRegistrar.disablePlugin` clears local bookkeeping; full
  subprocess teardown happens at process exit. v1.3 will add the SPI.

## Migration

The pre-existing `io.kairo.assistant.plugin.AssistantPlugin` interface in the
**kairo-assistant** dogfood repo is removed in the same release. It had two methods,
zero real implementations, and is unrelated to `io.kairo.api.plugin.Plugin` — there is
no migration path for users because there are no users. The four files
(`AssistantPlugin.java`, `PluginManager.java` plus the two test classes) are deleted
outright. Other code in kairo-assistant that referenced the old `PluginManager`
(`AssistantSession`, `AssistantAgentFactory`, `ReplSession`, `StatusController`) gets
re-pointed at the new `io.kairo.plugin.DefaultPluginManager`. Per Kairo's incubation
philosophy, no compat shim.

## References

- Implementation: `kairo-plugin/`, `kairo-api/src/main/java/io/kairo/api/plugin/`,
  `kairo-api/src/main/java/io/kairo/api/agent/Subagent*.java`
- Proposal (full design): `.plans/V1.2-PLUGIN-SPI-PROPOSAL.md`
- Claude Code plugin reference: <https://code.claude.com/docs/en/plugins-reference.md>
- Cross-ADRs: ADR-019 (hook API), ADR-021 (channel SPI shape inspiration),
  ADR-023 (SPI stability policy applied), ADR-028 (bridge protocol — adjacent topic)
