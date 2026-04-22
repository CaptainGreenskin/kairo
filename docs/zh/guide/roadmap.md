# 路线图

| 版本 | 主题 | 状态 |
|------|------|------|
| v0.1–v0.4 | 核心运行时 + SPI + A2A + 中间件 + 快照 | ✅ 已完成 |
| v0.5 | 会记忆的 Agent — Memory SPI + Embedding + 检查点/回滚 | 下一个 |
| v0.6 | 安全的 Agent — Guardrail SPI + 团队模式 | 计划中 |
| v0.7.0 | Guardrail SPI + MCP 安全 + 结构化异常 | ✅ 已完成 |
| v0.7.1 | Tool Result Budget + 结构化可观测性 | ✅ 已完成 |
| v0.8+ | Channel SPI + 仪表盘 + 执行回放 | 计划中 |

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

## v0.8+：完整平台（计划中）

Channel SPI 用于多渠道通信、Web 仪表盘用于 Agent 监控，以及执行回放用于调试和审计。
