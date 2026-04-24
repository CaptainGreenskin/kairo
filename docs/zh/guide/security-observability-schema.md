# 安全与事件可观测性 Schema

本文定义 Kairo 事件总线 → OpenTelemetry 集成的字段命名约定与基数约束。

**状态：** v0.9 起 schema 覆盖所有 `KairoEventBus` 域，不再仅限 security。原有 `security.*` 键早于 bus（见 ADR-018），为与现存 `LoggingSecurityEventSink` 消费者兼容，**在信封 body 内原样保留**。当 `KairoEventOTelExporter` 把信封桥接到 OpenTelemetry 时，会在外层额外套一层 `kairo.<domain>.*` 命名空间前缀。

## 属性命名空间

`KairoEventOTelExporter` 发出的每条 log record 都带有三个信封级键：

| 键 | 类型 | 示例 |
|---|---|---|
| `kairo.event.id` | String (UUID) | `9c1b…-…-…` |
| `kairo.domain` | String (enum) | `security`、`execution`、`evolution`、`team` |
| `kairo.event.type` | String | `GUARDRAIL_DENY`、`MODEL_TURN`、`EXPERT_TEAM_ROUND` |

其余按域划分的属性被平展在 `kairo.<domain>.<key>` 下——exporter 把 `KairoEvent.attributes()` 的每个条目复制进该命名空间。取值统一转成 String；结构化 payload 必须由发布者在入 bus 前展平。

Severity 映射：

- `security` → `WARN`
- `execution` / `evolution` / `team` → `INFO`

`LogRecord.severityText` 取域名；`LogRecord.body` 取 event type；`LogRecord.observedTimestamp` 使用 `KairoEvent.timestamp()`。

## 按域 Schema

### `kairo.security.*`

用于 guardrail 生命周期事件。OTel exporter 始终开启（不被采样丢弃）。

| 字段 | 类型 | 说明 | 示例 |
|---|---|---|---|
| `kairo.security.event.type` | `SecurityEventType` enum | 安全决策分类 | `GUARDRAIL_DENY` |
| `kairo.security.policy.name` | String (有限集合) | 产出决策的策略 | `content-filter` |
| `kairo.security.decision.action` | `ALLOW` / `DENY` / `MODIFY` / `WARN` | 策略输出 | `DENY` |
| `kairo.security.decision.reason` | String (最多 256 字符) | 人类可读原因 | `PII detected in output` |
| `kairo.security.target.name` | String (有限集合) | 被守护的工具或模型 | `echo`、`claude-3` |
| `kairo.security.target.type` | `MODEL` / `TOOL` / `MCP_TOOL` | 目标类型 | `TOOL` |
| `kairo.security.agent.name` | String (有限集合) | 拥有该 pipeline 的 Agent | `assistant` |
| `kairo.security.guardrail.phase` | `GuardrailPhase` enum | pipeline 切入点 | `PRE_TOOL` |

### `kairo.execution.*`

用于执行日志生命周期（模型回合、工具调用、压缩、迭代）。通过 `kairo.observability.event-otel.sampling-ratio` 采样。

| 字段 | 类型 | 说明 | 示例 |
|---|---|---|---|
| `kairo.execution.model` | String (有限集合) | 模型标识 | `claude-opus-4-7` |
| `kairo.execution.tool` | String (有限集合) | 适用时的工具名 | `bash`、`read` |
| `kairo.execution.phase` | Enum | 生命周期阶段 | `MODEL_TURN`、`TOOL_CALL`、`COMPACT` |
| `kairo.execution.iteration` | Integer | 单次运行内的迭代序号 | `3` |
| `kairo.execution.tokens.input` | Integer | 输入 token 数 | `12540` |
| `kairo.execution.tokens.output` | Integer | 输出 token 数 | `812` |
| `kairo.execution.duration_ms` | Integer | span 耗时毫秒 | `1824` |

### `kairo.evolution.*`

用于自进化技能治理生命周期（proposal → review → apply）。

| 字段 | 类型 | 说明 | 示例 |
|---|---|---|---|
| `kairo.evolution.skill.id` | String (有限集合) | 技能标识 | `summarize-diff` |
| `kairo.evolution.phase` | Enum | 治理阶段 | `PROPOSED`、`REVIEWED`、`APPLIED`、`ROLLED_BACK` |
| `kairo.evolution.reviewer` | String (有限集合) | 审阅主体 | `sre-ops` |
| `kairo.evolution.outcome` | Enum | 审阅结论 | `ACCEPTED`、`REJECTED` |

### `kairo.team.*`

用于多 Agent 编排生命周期（Expert Team，v0.10+ 表面开始填充）。

| 字段 | 类型 | 说明 | 示例 |
|---|---|---|---|
| `kairo.team.id` | String (有限集合) | 团队标识 | `triage-team` |
| `kairo.team.round` | Integer | 会话内回合序号 | `2` |
| `kairo.team.role` | String (有限集合) | 出场角色 | `triage`、`analyst`、`reporter` |
| `kairo.team.expert.id` | String (有限集合) | 专家 Agent id | `log-reader` |
| `kairo.team.transition` | Enum | 状态转移 | `ROUND_START`、`ROUND_END`、`HANDOFF`、`CONSENSUS` |

## 低基数约束

上述每个域的属性值都保持在可枚举的有限集合内：

- **禁止**把 request id、trace id、UUID 作为*属性值*（`kairo.event.id` 是故意为之的例外，单事件单值）。
- **禁止**原始输入内容（prompt、工具参数、模型响应）——必须留在原始 bus `payload` 中，不导出。
- **禁止**无界的用户自定义字符串。

若属性本身高基数，必须在发布前桶化（例如耗时 → OTel SDK 侧 histogram；用户 id → 角色）。

## 属性脱敏

`kairo.observability.event-otel.redact-attribute-patterns` 是一个 regex 列表，匹配*平铺*键（命名空间拼接后）。命中时，值会在挂到 log record 之前被字面量 `<redacted>` 替换。常用起步配置：

```yaml
kairo:
  observability:
    event-otel:
      redact-attribute-patterns:
        - ".*password.*"
        - ".*token.*"
        - ".*secret.*"
```

脱敏发生在 exporter 而非发布者——bus 仍会看到原始属性值，但外部观测后端看不到。

## OTel 集成（v0.9）

`kairo-spring-boot-starter-observability` 会在以下条件满足时装配 `KairoEventOTelExporter`：

1. `kairo.observability.event-otel.enabled=true`（默认 `false`，与 v0.9 其他 starter 一致）。
2. 存在 `KairoEventBus` bean（只要 `kairo-spring-boot-starter-core` 在 classpath 就总是成立）。
3. 存在 `LoggerProvider` bean（应用自行接入 OTel SDK——可以用 `opentelemetry-spring-boot-starter` 或手写配置）。

默认 `include-domains` 为 `[security]`。execution / team / evolution 域需要显式开启：

```yaml
kairo:
  observability:
    event-otel:
      enabled: true
      include-domains: [security, execution, evolution]
      sampling-ratio: 0.2     # 非 security 域采样；security 总是全量
```

原有 `LoggingSecurityEventSink` 继续并行工作——它用遗留 `security.*` 键（无 `kairo.` 前缀）写结构化 SLF4J 条目，现有 dashboard 在团队迁移到 OTel 路径期间仍可正常运行。域 / 信封契约见 ADR-018，exporter 行为保证见 ADR-022。
