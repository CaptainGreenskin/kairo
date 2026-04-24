# 升级指南：v0.7.x → v0.8.0

本指南覆盖 Kairo v0.8.0 引入的变更，以及从 v0.7.x 升级时需要关注的事项。

> **TL;DR——既有用户无需动作。** 所有新特性均为 opt-in，既有 Agent 配置保持可用。

---

## 新 SPI（Experimental）

### DurableExecutionStore

提供基于 checkpoint 的崩溃恢复与至少一次语义。两种内置实现：

- `InMemoryDurableExecutionStore`——开发与测试
- `JdbcDurableExecutionStore`——生产持久化

**状态**：`@Experimental`——次版本内 API 可能变动。

### ResourceConstraint

统一迭代 / token / 超时的执行约束，替代散点检查。契约为 `validate()` + `onViolation()`，约束动作可组合（`ALLOW`、`WARN_CONTINUE`、`GRACEFUL_EXIT`、`EMERGENCY_STOP`）。

**状态**：`@Experimental`——次版本内 API 可能变动。

---

## ToolContext 破坏式变更

`ToolContext` 新增可选字段 `idempotencyKey`。

- **使用 3-arg 构造器的既有代码**向后兼容——该键默认 `null`。
- 如果手动构造 `ToolContext`，除非想启用幂等支持，否则无需改动。

---

## IterationGuards 变更

当存在 `ResourceConstraint` 时，`IterationGuards` 现在会委派给其链路。

- 未注入 `ResourceConstraint` 时**行为不变**。
- 如果提供了自定义 `ResourceConstraint` 实现，会与既有 iteration guard 逻辑组合。

---

## ReActLoop 构造器

`ReActLoop` 新增 6-arg 重载，可接受可选的 `ExecutionEventEmitter`。

- **既有 5-arg 构造器继续可用**，行为与旧版一致。
- 新重载用于开启持久化执行的事件发射。

---

## 新注解

`io.kairo.api.tool` 下新增两个注解，用于工具回放安全：

| 注解 | 含义 |
|---|---|
| `@Idempotent` | 工具可在 replay 中安全重放（例如只读查询） |
| `@NonIdempotent` | 工具**不可**在 replay 中重放；改用缓存结果 |

**默认行为**：未标注的工具被视为**非幂等**（安全默认——replay 走缓存结果）。

---

## 新 Spring Boot 属性

所有新属性都是 **opt-in、默认关闭**。

### 持久化执行

| 属性 | 默认 | 说明 |
|---|---|---|
| `kairo.execution.durable.enabled` | `false` | 启用 DurableExecution |
| `kairo.execution.durable.store-type` | `memory` | `"memory"` 或 `"jdbc"` |
| `kairo.execution.durable.recovery-on-startup` | `true` | 启动时自动恢复 pending execution |

### 成本感知路由

| 属性 | 默认 | 说明 |
|---|---|---|
| `kairo.routing.model-tiers` | *(无)* | 模型 tier 定义与每 token 定价 |
| `kairo.routing.fallback-chain` | *(无)* | 按序排列的 fallback tier 名列表 |

---

## JDBC 迁移

若开启 `kairo.execution.durable.store-type=jdbc`：

- Flyway 迁移 `V1__create_execution_tables.sql` 在应用启动时**自动执行**。
- 迁移创建所需的 `execution_records` 和 `execution_events` 表。
- 确保数据源已配置、Flyway 在 classpath 上（通过 `kairo-spring-boot-starter` 传递依赖）。

---

## 小结

| 范围 | 影响 | 是否需要动作 |
|---|---|---|
| DurableExecutionStore SPI | 新增（Experimental） | 无——通过属性开启 |
| ResourceConstraint SPI | 新增（Experimental） | 无——通过注入 bean 开启 |
| ToolContext 字段 | 增量 | 无——默认 null |
| IterationGuards | 行为（委派） | 无——无 constraint 时不变 |
| ReActLoop 构造器 | 新重载 | 无——既有构造器仍可用 |
| @Idempotent / @NonIdempotent | 新注解 | 无——未标注默认安全 |
| Spring Boot 属性 | 新增（opt-in） | 无——默认关闭 |
| JDBC 迁移 | 选择 jdbc 时自动执行 | 使用 jdbc store 时配置数据源 |
