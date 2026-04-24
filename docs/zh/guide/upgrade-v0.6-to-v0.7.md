# 升级指南：v0.6 → v0.7

本指南覆盖 Kairo v0.7 的所有变更，以及从 v0.6 升级需要的步骤。

## 破坏式变更

### MCP 安全：默认策略改为 DENY_SAFE

MCP server 现默认 `DENY_SAFE`——未配置的工具**被拦截**。此前所有工具隐式放行。

**谁会受影响？** 任何使用 `kairo-mcp` 但未显式配置安全策略的项目。

**迁移选项：**

**选项 A（推荐）：** 为每个 MCP server 显式配置 `allowedTools`：

```yaml
kairo:
  mcp:
    servers:
      my-server:
        securityPolicy: DENY_SAFE
        allowedTools:
          - "read_file"
          - "search"
```

**选项 B：** 用 `ALLOW_ALL` 恢复旧行为（生产环境不推荐）：

```yaml
kairo:
  mcp:
    servers:
      my-server:
        securityPolicy: ALLOW_ALL
```

**`McpServerConfig` 新增字段：**

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `securityPolicy` | `McpSecurityPolicy` | `DENY_SAFE` | `ALLOW_ALL`、`DENY_SAFE` 或 `DENY_ALL` |
| `allowedTools` | `Set<String>` | `null` | `DENY_SAFE` 下显式放行的工具 |
| `deniedTools` | `Set<String>` | `Set.of()` | `ALLOW_ALL` 下显式拦截的工具 |
| `schemaValidation` | `boolean` | `true` | 按 JSON Schema 校验工具入参 |
| `maxConcurrentCalls` | `int` | `10` | 单 server 并发工具调用上限 |

`McpStaticGuardrailPolicy` 在 guardrail 链中最早执行（`order = Integer.MIN_VALUE`），确保 MCP 安全始终早于自定义策略。

---

## 新特性

### 异常 Phase B：结构化错误字段

`KairoException` 现携带结构化字段供程序化处理：

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `errorCode` | `String` | `null` | 机器可读错误码（例 `"MODEL_RATE_LIMITED"`） |
| `category` | `ErrorCategory` | `null` | 错误域分类 |
| `retryable` | `boolean` | `false` | 该操作是否可重试 |
| `retryAfterMs` | `Long` | `null` | 建议重试延迟毫秒 |

**`ErrorCategory` 枚举值：** `MODEL`、`TOOL`、`AGENT`、`STORAGE`、`SECURITY`、`UNKNOWN`

**迁移：** 无需改代码。所有既有构造器仍可用——新字段默认 `null`/`false`。子类提供合理默认（例如 `ModelRateLimitException` → `errorCode="MODEL_RATE_LIMITED"`、`retryable=true`）。

若要用上结构化字段，使用新构造器或调用 getter：

```java
try {
    // ...
} catch (KairoException e) {
    if (e.isRetryable()) {
        long delay = e.getRetryAfterMs() != null ? e.getRetryAfterMs() : 1000;
        // 安排重试
    }
    log.error("[{}] {}: {}", e.getCategory(), e.getErrorCode(), e.getMessage());
}
```

### Guardrail SPI（@Experimental）

一个全新的 4-phase 输入/输出拦截框架：

- **Phase：** `PRE_MODEL`、`POST_MODEL`、`PRE_TOOL`、`POST_TOOL`
- **核心 SPI：** `GuardrailPolicy`——实现并注册为 Spring bean
- **评估：** `DefaultGuardrailChain` 按 order 评估，遇 `DENY` 短路
- **类型安全：** `GuardrailPayload` 是 sealed interface，带类型化变体（`ModelInput`、`ModelOutput`、`ToolInput`、`ToolOutput`）

**接入示例：**

```java
@Component
@Order(100)
public class ContentFilter implements GuardrailPolicy {
    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (context.phase() == GuardrailPhase.PRE_MODEL
                && context.payload() instanceof GuardrailPayload.ModelInput input) {
            // 检查输入，返回 ALLOW 或 DENY
        }
        return Mono.just(GuardrailDecision.allow());
    }
}
```

**既有代码零改动**——空 guardrail 链即 no-op。

### 安全可观测性（@Experimental）

每个 guardrail 决策都会被记录为 `SecurityEvent`，用于审计与合规。

- **`SecurityEvent`**——不可变 record，含 phase、策略名、决策、时间戳、payload 摘要
- **`SecurityEventSink`** SPI——实现后可自定义处理（例如写 DB 或 SIEM）
- **默认：** `LoggingSecurityEventSink` 以 INFO 级别记录

> **注：** Security 事件的 OTel exporter 推到 v0.8。

### 成本路由扩展点（@Experimental）

为未来的成本感知模型路由预留扩展点：

- **`RoutingPolicy`** SPI——实现后可按成本、延迟等维度影响模型选择
- **`costBudget`** 字段挂在 `ModelConfig` 上（nullable，v0.7 不做强制）
- **`DefaultRoutingPolicy`**——no-op 占位实现

> **注：** v0.7 不运行任何路由逻辑，仅埋扩展点。

---

## ADR

v0.7 敲定的架构决策：

- **ADR-007：** Guardrail SPI 设计
- **ADR-008：** 异常 Phase B 结构化字段
- **ADR-009：** MCP 安全默认策略
