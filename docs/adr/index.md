# 架构决策记录（ADR）

ADR（Architecture Decision Record）记录 Kairo 项目中每一个重要的架构决策——做了什么选择、为什么这样选、考虑过哪些替代方案、有什么已知的 trade-off。

代码告诉你系统"是"什么，ADR 告诉你"为什么"是这样。

---

## 核心运行时

- [ADR-001: ReAct Loop Decomposition](ADR-001-react-loop-decomposition.md)
- [ADR-006: Compaction Pipeline Architecture](ADR-006-compaction-pipeline-architecture.md)
- [ADR-011: DurableExecutionStore SPI Design](ADR-011-durable-execution-store-spi.md)
- [ADR-012: ResourceConstraint SPI Design](ADR-012-execution-constraint-spi.md)
- [ADR-013: Cost-Aware Routing Design](ADR-013-cost-aware-routing.md)
- [ADR-017: AgentConfig Capability Pattern](ADR-017-agent-config-capability-pattern.md)

## 工具与安全

- [ADR-003: Cooperative Cancellation Semantics](ADR-003-cooperative-cancellation-semantics.md)
- [ADR-007: Guardrail SPI Design](ADR-007-guardrail-spi-design.md)
- [ADR-009: MCP Security Default Policy](ADR-009-mcp-security-default-policy.md)
- [ADR-010: ToolResultBudget Contract](ADR-010-tool-result-budget-contract.md)
- [ADR-024: PII Redaction as a Guardrail Policy](ADR-024-pii-redaction-as-guardrail-policy.md)
- [ADR-025: ExecutionSandbox SPI](ADR-025-execution-sandbox-spi.md)

## 架构与扩展

- [ADR-002: MCP Module Decoupling](ADR-002-mcp-module-decoupling.md)
- [ADR-004: Exception Hierarchy Design](ADR-004-exception-hierarchy-design.md)
- [ADR-005: Provider Decomposition Template](ADR-005-provider-decomposition-template.md)
- [ADR-008: Exception Phase B — Structured Fields](ADR-008-exception-phase-b-structured-fields.md)
- [ADR-023: SPI Stability Policy](ADR-023-spi-stability-policy.md)
- [ADR-026: Workspace SPI](ADR-026-workspace-spi.md)

## Hook 与治理

- [ADR-019: Hook API Consolidation](ADR-019-hook-api-consolidation.md)
- [ADR-018: Unified Event Bus](ADR-018-unified-event-bus.md)
- [ADR-022: KairoEventBus → OpenTelemetry Exporter](ADR-022-kairo-event-otel-exporter.md)

## 能力模块

- [ADR-014: Agent Self-Evolution](ADR-014-agent-self-evolution.md)
- [ADR-015: Expert Team Orchestration](ADR-015-expert-team-orchestration.md)
- [ADR-016: Coordinator SPI](ADR-016-coordinator-spi.md)
- [ADR-020: Skill Subsystem Unification](ADR-020-skill-subsystem-unification.md)
- [ADR-029: Plugin SPI with Claude Code Format Compatibility](ADR-029-plugin-spi-claude-code-compat.md)

## 传输与网关

- [ADR-021: Channel SPI](ADR-021-channel-spi.md)
- [ADR-027: TenantContext](ADR-027-tenant-context.md)
- [ADR-028: Bridge Protocol](ADR-028-bridge-protocol.md)
- [ADR-030: Gateway Module](ADR-030-gateway-module.md)
