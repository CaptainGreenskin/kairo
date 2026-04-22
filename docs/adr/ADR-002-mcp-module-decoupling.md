# ADR-002: MCP Module Decoupling

## Status

Accepted (v0.5.1)

## Context

The `kairo-mcp` module depended on `kairo-core` solely because `McpToolExecutor` implemented
the `ToolHandler` interface, which was defined in `kairo-core`. This created a transitive
dependency chain: any module using MCP capabilities pulled in the entire core runtime, even
though MCP integration only needed the SPI contract.

This coupling prevented third-party MCP adapters from being developed without depending on
`kairo-core`, and it made the module dependency graph wider than necessary — violating the
principle that SPI contracts should live in a thin API module.

## Decision

Move the `ToolHandler` interface from `kairo-core` to `kairo-api`. The `kairo-mcp` module
now depends only on `kairo-api`, not `kairo-core`.

Specifically:
1. `ToolHandler` moved from `io.kairo.core.tool` → `io.kairo.api.tool`.
2. All references across the codebase updated to the new package path.
3. `kairo-mcp/pom.xml` dependency changed from `kairo-core` to `kairo-api`.
4. 37 files updated for the import change across kairo-core, kairo-mcp, kairo-tools,
   kairo-examples, and kairo-spring-boot-starter.

## Consequences

- **Positive**: `kairo-mcp` can evolve independently of `kairo-core`. Bug fixes and feature
  additions to MCP support no longer risk regressions in core agent logic.
- **Positive**: Third-party MCP adapters can depend solely on `kairo-api` (the thin SPI
  module), reducing their dependency footprint.
- **Positive**: The module dependency graph is cleaner:
  `kairo-mcp → kairo-api` instead of `kairo-mcp → kairo-core → kairo-api`.
- **Negative**: The one-time migration required updating 37 files. Any external code importing
  `ToolHandler` from the old package must update imports.
- **Negative**: This sets a precedent that SPI interfaces must live in `kairo-api`, which
  requires ongoing governance (see `docs/en/guide/spi-governance.md`).

## References

- `ToolHandler.java` — `io.kairo.api.tool.ToolHandler` (new location)
- `kairo-mcp/pom.xml` — dependency on `kairo-api` only
- `docs/en/guide/spi-governance.md` — SPI registry and governance policy
