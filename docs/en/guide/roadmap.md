# Roadmap

| Version | Theme | Status |
|---------|-------|--------|
| v0.1–v0.4 | Core Runtime + SPI + A2A + Middleware + Snapshot | Implemented |
| v0.5 | Agents That Remember — Memory SPI + Embedding + Checkpoint/Rollback | Implemented |
| v0.6 | Agents That Are Safe — Guardrail SPI + Interrupt/Resume + Team Patterns | Planned v0.6 |
| v0.7.0 | Guardrail SPI + MCP Security + Structured Exceptions | Implemented |
| v0.7.1 | Tool Result Budget + Structured Observability | Implemented |
| v0.8+ | Metrics + Execution Event Stream + Dashboard + Execution Replay | Planned v0.8 |

## v0.1–v0.4: Core Runtime (Implemented)

The foundation is in place: ReAct engine, SPI architecture, 21 built-in tools, context compaction, model providers (Anthropic, GLM, Qwen, GPT), A2A Protocol, Middleware Pipeline, Agent Snapshot, and Spring Boot integration.

## v0.5: Agents That Remember (Implemented)

Memory SPI with embedding-based retrieval, persistent checkpoint/rollback, and durable execution support.

## v0.6: Agents That Are Safe (Planned v0.6)

Guardrail SPI for input/output validation, interrupt/resume support, team collaboration patterns, and enhanced permission management.

## v0.7.0: Guardrail SPI + MCP Security (Implemented)

Guardrail SPI for 4-phase interception, MCP security with default deny-safe policy, structured error fields on KairoException, cost routing SPI, and security observability.

## v0.7.1: Tool Result Budget (Implemented)

ToolResultBudget L0 pre-truncation, structured observability metadata on ToolResult, TOOL message observability fields, tool exception/policy path classification, and ADR-010.

## v0.8+: Full Platform (Planned v0.8)

Metrics collection (OpenTelemetry metrics export), execution event stream, web dashboard for agent monitoring, and execution replay for debugging and auditing.
