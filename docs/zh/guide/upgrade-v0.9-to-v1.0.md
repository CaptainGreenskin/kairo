# 升级指南：v0.9 → v1.0

本指南覆盖 v0.10.0 ~ v1.0.0 GA 的完整变更路径。这是 Kairo 从孵化到稳定的关键跨越——SPI 全面标注 `@Stable` / `@Experimental`，japicmp 兼容性门控上线，新增 PII 脱敏和审计模块。

---

## 破坏式变更

### AgentConfig MCP 字段物理删除（v0.10.2）

`AgentConfig` record 中以下 4 个字段已被物理删除：

- `mcpServerConfigs`
- `mcpMaxToolsPerServer`
- `mcpStrictSchemaAlignment`
- `mcpToolSearchQuery`

**谁会受影响？** 任何直接构造 `AgentConfig` 或访问这些字段的代码。

**迁移步骤：**

```java
// 旧
config.mcpServerConfigs();
config.mcpMaxToolsPerServer();

// 新：通过 capability 访问
config.mcpCapability().serverConfigs();
config.mcpCapability().maxToolsPerServer();
```

### Hook API 重组（v0.10.0）

Hook 相关 API 统一到 `HookPhase` 枚举 + `@HookHandler` 注解模式。

**迁移步骤：**

```java
// 旧：分散的 Hook 接口
public class MyHook implements PreModelHook { ... }

// 新：统一的 @HookHandler
@HookHandler(phase = HookPhase.PRE_MODEL_CALL)
public Mono<HookDecision> onPreModel(HookContext ctx) {
    return Mono.just(HookDecision.CONTINUE);
}
```

### kairo-skill 模块拆分（v0.10.2）

Skill 相关类从 `kairo-core` / `kairo-evolution` 迁移到独立的 `kairo-skill` 模块：

- 包名变更：`io.kairo.core.skill` → `io.kairo.skill`
- 7 个生产类已重定位

**迁移步骤：** 更新 import 语句，添加 `kairo-skill` 依赖。

### ProviderPipeline SPI 接入（v0.10.2）

`AnthropicProvider` 和 `OpenAIProvider`（及各自 4 个委托类）现在实现 `ProviderPipeline<String, String>` SPI（ADR-005）。如果你有自定义 Provider 实现，建议同步接入。

---

## 新功能

### AgentConfig Capability Pattern（v0.10.0）

模型/MCP/Memory 等配置项按能力域分组，替代扁平字段。设计文档：ADR-017。

### KairoEventBus 统一事件总线（v0.10.0）

`KairoEventBus` 注册为默认 `@Bean`，`SecurityEventSink` 通过 `BusBridgingSecurityEventSink` 桥接。设计文档：ADR-018。

### SkillStore SPI（v0.10.0）

技能子系统统一到 `SkillStore` SPI。设计文档：ADR-020。

### Expert Team Orchestration MVP（v0.10.1）

新增 `kairo-expert-team` 模块，提供 plan/generate/evaluate 三阶段协调管线：

- `ExpertTeamCoordinator`——管线入口
- `EvaluationStrategy` SPI——评估策略扩展点
- `DefaultPlanner`——默认规划器
- 启用：`kairo.expert-team.enabled=true`
- 设计文档：ADR-015 / ADR-016

### SPI 稳定性标注（v1.0.0-RC1）

`kairo-api` 全量标注完成：**119 `@Stable` + 78 `@Experimental`**，覆盖 197 个类型。`japicmp-maven-plugin` 作为兼容性门控上线（`api-compat` profile）。设计文档：ADR-023。

### kairo-security-pii 模块（v1.0.0 GA）

PII 脱敏 + 审计合规，基于现有 SPI，不引入新抽象：

| 组件 | 实现的 SPI | 说明 |
|---|---|---|
| `PiiRedactionPolicy` | `GuardrailPolicy` | EMAIL/PHONE/CREDIT_CARD/SSN/API_KEY/JWT 脱敏 |
| `JdbcAuditEventSink` | `SecurityEventSink` | append-only 审计表，Flyway 自动建表 |
| `ComplianceReportCollector` | `Consumer<KairoEvent>` | 按运行生成 Markdown 审计报告 |

设计文档：ADR-024。

---

## 新增依赖

```xml
<!-- Expert Team（opt-in） -->
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-spring-boot-starter-expert-team</artifactId>
</dependency>

<!-- Skill 模块（v0.10.2 拆分后需显式引入） -->
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-skill</artifactId>
</dependency>

<!-- PII 脱敏 + 审计（v1.0.0） -->
<dependency>
    <groupId>io.kairo</groupId>
    <artifactId>kairo-security-pii</artifactId>
</dependency>
```

---

## 已弃用

v0.10.0 引入了增量 deprecation 标记，为后续版本的删除做准备。具体 deprecated 项在 `kairo-api` 的 `@Deprecated` javadoc 中标注了替代方案。

---

## ADR

- **ADR-015 / ADR-016**：Expert Team Orchestration
- **ADR-017**：AgentConfig Capability Pattern
- **ADR-018**：Unified Event Bus
- **ADR-019**：Hook API Consolidation
- **ADR-020**：Skill Subsystem Unification
- **ADR-023**：SPI Stability Policy
- **ADR-024**：PII as GuardrailPolicy
