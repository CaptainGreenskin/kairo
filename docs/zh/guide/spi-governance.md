# SPI 治理

本文定义 Kairo SPI（Service Provider Interface）的治理模型：SPI 注册表、兼容性等级、弃用策略、迁移指南模板。

> v1.0 起，稳定性不再仅凭 Javadoc 约定，而是通过 `@Stable` / `@Experimental` / `@Internal` 注解机械标识，并由 japicmp 在 `kairo-api` 构建时拦截。详见 [ADR-023](../../adr/ADR-023-spi-stability-policy.md) 与 [japicmp 政策](../../governance/japicmp-policy.md)。

## SPI 注册表

所有公开 SPI 的位置与稳定性等级：

| SPI 接口 | 模块 | Package | 稳定性 |
|----------|------|---------|--------|
| `ModelProvider` | kairo-api | `io.kairo.api.model` | Stable |
| `ToolHandler` | kairo-api | `io.kairo.api.tool` | Stable |
| `ToolExecutor` | kairo-api | `io.kairo.api.tool` | Stable |
| `McpPlugin` | kairo-api | `io.kairo.api.mcp` | Stable |
| `MemoryStore` | kairo-api | `io.kairo.api.memory` | Stable |
| `EmbeddingProvider` | kairo-api | `io.kairo.api.memory` | Experimental |
| `UserApprovalHandler` | kairo-api | `io.kairo.api.tool` | Stable |
| `ElicitationHandler` | kairo-mcp | `io.kairo.mcp` | Experimental |
| `GuardrailPolicy` | kairo-api | `io.kairo.api.guardrail` | Experimental |
| `SecurityEventSink` | kairo-api | `io.kairo.api.guardrail` | Experimental |
| `RoutingPolicy` | kairo-api | `io.kairo.api.routing` | Experimental |
| `DurableExecutionStore` | kairo-api | `io.kairo.api.execution` | Experimental |
| `ResourceConstraint` | kairo-api | `io.kairo.api.execution` | Experimental |

## 兼容性等级

### Stable

次版本内保持向后兼容。破坏式变更只能随主版本，且需**提前 2 个次版本预告**。

- 同一主版本内方法签名不变。
- 接口新增方法必须带 `default` 实现，原实现可继续编译。
- Javadoc 中描述的行为语义属于契约。

### Experimental

次版本内可变。形式上由 `@Experimental` 注解标识（早期版本用 `@apiNote`）。

- 基于真实反馈演进形态。
- 次版本之间可能破坏式变更。
- 形态稳定后可提升为 Stable。

### Internal

非公开 API，随时可变。

- `*.internal` package 或 `@Internal` 注解。
- 无任何向后兼容承诺。
- 外部代码不应引用。

## 弃用策略

1. 任何 Stable SPI 删除前至少预告 **2 个次版本**。
2. 使用 `@Deprecated(since = "x.y", forRemoval = true)`。
3. CHANGELOG 给出**迁移指南**。
4. 替代 SPI 必须与 deprecation 同一版本发布。
5. Experimental SPI 可只预告 1 个次版本。

### 时间线示例

| 版本 | 行为 |
|------|------|
| v0.7 | `OldSpi` 弃用；`NewSpi` 推出 |
| v0.8 | `OldSpi` 仍在，迁移文档就位 |
| v0.9 | `OldSpi` 删除 |

## 迁移指南模板

SPI 弃用并替换时，按此模板在 CHANGELOG 追加：

```
## Migration: [Old SPI] → [New SPI]

**Version**: v0.X → v0.Y

**Reason**: [变更理由]

**Steps**:
1. [步骤 1 —— 例如替换 import 到新 package]
2. [步骤 2 —— 例如重命名方法调用]
3. [步骤 3 —— 例如更新配置]

**Compatibility**: [Old SPI] 在 v0.X 弃用，将在 v0.Z 移除
```

## 实现方须遵守

1. **线程安全**：除非显式说明，SPI 实现必须并发安全。
2. **反应式类型**：返回 `Mono` / `Flux` 的 SPI 不得阻塞调用线程；阻塞 I/O 请 `subscribeOn(Schedulers.boundedElastic())`。
3. **错误处理**：抛领域异常（`ToolException`、`MemoryStoreException` 等）而非通用异常。
4. **协作式取消**：长任务须观察 Reactor Context 的 `CancellationSignal` 并及时收尾。

## 版本参考

| SPI 接口 | 引入版本 |
|----------|----------|
| `ModelProvider` | v0.1.0 |
| `ToolHandler` | v0.1.0 |
| `ToolExecutor` | v0.1.0 |
| `MemoryStore` | v0.1.0 |
| `McpPlugin` | v0.4.0 |
| `UserApprovalHandler` | v0.4.0 |
| `EmbeddingProvider` | v0.5.0 |
| `ElicitationHandler` | v0.5.0 |
| `GuardrailPolicy` | v0.7.0 |
| `SecurityEventSink` | v0.7.0 |
| `RoutingPolicy` | v0.7.0 |
| `DurableExecutionStore` | v0.8.0 |
| `ResourceConstraint` | v0.8.0 |
