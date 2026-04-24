# 路线图

| 版本 | 主题 | 状态 |
|------|------|------|
| v0.1–v0.4 | 核心运行时 + SPI + A2A + 中间件 + 快照 | ✅ 已完成 |
| v0.5 | 会记忆的 Agent — Memory SPI + Embedding + 检查点/回滚 | 下一个 |
| v0.6 | 安全的 Agent — Guardrail SPI + 团队模式 | 计划中 |
| v0.7.0 | Guardrail SPI + MCP 安全 + 结构化异常 | ✅ 已完成 |
| v0.7.1 | Tool Result Budget + 结构化可观测性 | ✅ 已完成 |
| v0.8 | 持久化执行 MVP + 执行约束 SPI + 成本感知路由 | ✅ 已实现 |
| v0.9 | 平台能力补缺口 + Channel SPI / Event Stream / OTel Exporter | ✅ 已实现 |
| v0.10 | 核心重构波次（事件总线 + capability 配置形状 + Hook 统一入口 + SPI 脚手架） | ✅ 已实现 |
| v0.10.1 | 专家团队编排 MVP（`TeamCoordinator` + `EvaluationStrategy` SPI + 可选 starter） | ✅ 已实现 |

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

## v0.9：平台能力补缺口 + Channel SPI / Event Stream / OTel Exporter（已实现）

v0.9.0 GA 合并两条线交付：

- **缺口补齐**：默认 Spring agent 自进化运行时接线的行为级验证、`ExecutionEventType` vs `EvolutionEventType` 事件域防回退测试、`kairo-core` 不依赖进化实现类的模块边界 guard。
- **平台能力 P0**：Channel SPI + `LoopbackChannel` 参考实现 + starter + TCK（ADR-021）；传输无关的事件流核心 + SSE / WebSocket 两条传输，统一落在 deny-safe 的 `KairoEventStreamAuthorizer` 契约下（ADR-018）；`kairo-observability` 内的 `KairoEventOTelExporter`，把 `KairoEvent` 桥接到 OTel logs API，支持 domain filter + 采样率 + 键正则脱敏，附独立 opt-in starter（ADR-022）。
- **D5 废弃 API 清理**：`io.kairo.api.task.*` 与 `TeamScheduler` 物理删除——原消费者已迁移到 v0.10 的 Expert Team coordinator 与 hook 链。

发布门禁:clean checkout 下 `mvn clean verify` 全绿,2,498 个测试覆盖 344 个套件。

验证证据见:`docs/roadmap/v0.9-release-verification.md`(取代 `v0.9-gap-only-verification.md`)。

## v0.10：核心重构波次（已实现）

本轮优先做**平台工程化**：引入进程内统一事件门面（`KairoEventBus`）、把横切配置收敛为 capability record（例如 `McpCapabilityConfig`）、提供 `@HookHandler` 与旧注解并存迁移路径，并补齐 `SkillStore` / `ProviderPipeline` 等 SPI 脚手架，降低后续 Expert Team / OTel / Channel 等能力的接入成本。

验证证据见：`docs/roadmap/v0.10-core-refactor-verification.md`。

## v0.10.1：专家团队编排 MVP（已实现）

该子里程碑把 Anthropic Harness 验证过的「Planner / Generator / Evaluator」三 Agent 架构以**基础设施一等公民**的形态落地：独立的 `kairo-expert-team` 模块 + 可选的 `kairo-spring-boot-starter-expert-team`，在 `kairo-api` 中严格只暴露两个 SPI：

- `TeamCoordinator` — `Mono<TeamResult> execute(TeamExecutionRequest, Team)`。
- `EvaluationStrategy` — `Mono<EvaluationVerdict> evaluate(EvaluationContext)`。

默认实现包含 `ExpertTeamCoordinator`（Planning → Generating → Evaluating → Terminal）、`SimpleEvaluationStrategy`（确定性 rubric）、`AgentEvaluationStrategy`（agent-invoker 接缝，把异常映射为 `VerdictOutcome.REVIEW_EXCEEDED`）和 `DefaultPlanner`（role-per-agent 顺序计划，`PlannerFailureMode.FAIL_FAST` 默认）。模块 test-jar 内附 TCK（`TeamCoordinatorTCK`、`EvaluationStrategyTCK`，以及 `RecordingEventBus` / `NoopMessageBus` / `StubAgent`）。

关键语义：

- **Evaluator 崩溃** 在 `RiskProfile.LOW` 下降级为 `TeamStatus.DEGRADED` + 警告；在 `MEDIUM|HIGH` 下直接 `TeamStatus.FAILED`。
- **Team 超时** 保留部分步骤产物，末事件为 `TEAM_TIMEOUT`。
- **事件域隔离**：coordinator 发布的所有事件都在 `KairoEvent.DOMAIN_TEAM`，不泄漏到 execution / evolution / security 域。
- **Starter 需显式开启** `kairo.expert-team.enabled=true`，仅引入 starter 本身不会装配 coordinator（与 ADR-015 "专家团队属于高阶 + 策略敏感能力" 的口径一致）。

Kickoff：`docs/roadmap/v0.10-expert-team-kickoff.md`。验证证据：`docs/roadmap/v0.10-expert-team-verification.md`。
