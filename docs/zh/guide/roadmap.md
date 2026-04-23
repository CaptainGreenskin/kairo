# 路线图

| 版本 | 主题 | 状态 |
|------|------|------|
| v0.1–v0.4 | 核心运行时 + SPI + A2A + 中间件 + 快照 | ✅ 已完成 |
| v0.5 | 会记忆的 Agent — Memory SPI + Embedding + 检查点/回滚 | 下一个 |
| v0.6 | 安全的 Agent — Guardrail SPI + 团队模式 | 计划中 |
| v0.7.0 | Guardrail SPI + MCP 安全 + 结构化异常 | ✅ 已完成 |
| v0.7.1 | Tool Result Budget + 结构化可观测性 | ✅ 已完成 |
| v0.8 | 持久化执行 MVP + 执行约束 SPI + 成本感知路由 | ✅ 已实现 |
| v0.9 | 平台能力补缺口（Self-Evolution 接线验证 + 防回退约束） | 进行中 |
| v0.10 | 核心重构波次（事件总线 + capability 配置形状 + Hook 统一入口 + SPI 脚手架） | ✅ 已实现 |

## v0.1–v0.4：核心运行时（已完成）

基础已就位：ReAct 引擎、SPI 架构、21 个内置工具、上下文压缩、模型提供者（Anthropic、GLM、Qwen、GPT）、A2A 协议、中间件管道、Agent 快照以及 Spring Boot 集成。

## v0.5：会记忆的 Agent（下一个）

Memory SPI，基于 Embedding 的检索、持久化检查点/回滚，以及持久执行支持。

## v0.6：安全的 Agent（计划中）

Guardrail SPI 用于输入/输出验证、团队协作模式和增强的权限管理。

## v0.7.0：Guardrail SPI + MCP 安全（已完成）

Guardrail SPI 四阶段拦截、MCP 安全默认 deny-safe 策略、KairoException 结构化错误字段、成本路由 SPI 和安全可观测性。

## v0.7.1：Tool Result Budget（已完成）

ToolResultBudget L0 预截断、ToolResult 结构化可观测性元数据、TOOL 消息可观测性字段、工具异常/策略路径分类和 ADR-010。

## v0.8：持久化执行 MVP + 执行约束 + 成本感知路由（已实现）

DurableExecutionStore SPI（InMemory + JDBC）支持 Agent 跨进程恢复（at-least-once 语义），ResourceConstraint SPI 统一执行约束 enforcement（替代分散的 iteration/token/timeout 检查），CostAwareRoutingPolicy 扩展 v0.7 RoutingPolicy SPI，提供 ModelTierRegistry 和线性降级链。

## v0.9：平台能力补缺口（进行中）

本轮聚焦补缺口而非重做：验证默认 Self-Evolution 运行时接线、补充架构防回退测试，并同步版本状态文档口径。

验证记录见：`docs/roadmap/v0.9-gap-only-verification.md`。

## v0.10：核心重构波次（已实现）

本轮优先做**平台工程化**：引入进程内统一事件门面（`KairoEventBus`）、把横切配置收敛为 capability record（例如 `McpCapabilityConfig`）、提供 `@HookHandler` 与旧注解并存迁移路径，并补齐 `SkillStore` / `ProviderPipeline` 等 SPI 脚手架，降低后续 Expert Team / OTel / Channel 等能力的接入成本。

验证证据见：`docs/roadmap/v0.10-core-refactor-verification.md`。
