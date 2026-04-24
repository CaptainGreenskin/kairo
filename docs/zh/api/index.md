# API 参考

`io.kairo.api.*` 下每个类型都带有显式的稳定性注解：

| 注解 | 含义 | 破坏策略 |
|------|------|----------|
| `@Stable` | 公开契约，v1.x 内冻结 | 需 ADR + 主版本 bump |
| `@Experimental` | opt-in，形态可变 | 可随次版本调整 |
| `@Internal` | 非公开 API | 随时变更 |

标准政策：[ADR-023 — SPI 稳定性](../../adr/ADR-023-spi-stability-policy.md)。
机械约束：[japicmp 政策](../../governance/japicmp-policy.md)。
全量 triage：[SPI census v1.0](../../governance/spi-census-v1.0.md)。

## 核心 `@Stable` SPI

| 类型 | Package | 职责 |
|------|---------|------|
| [Agent](./Agent.md) | `agent` | 可运行 Agent 的 ReAct 核心契约 |
| [ModelProvider](./ModelProvider.md) | `model` | 调用 LLM，返回 `ModelResponse` |
| [ToolHandler](./ToolHandler.md) | `tool` | 执行具名工具调用 |
| [Msg](./Msg.md) | `message` | Agent / Provider / Tool 间的中立消息封装 |
| [KairoException](./KairoException.md) | `exception` | 结构化错误字段的根异常 |

## 其余类型的查找路径

- **`@Stable` 面（119 types）**—— 在 census 中按 package 归并列出。
- **`@Experimental` 面（78 types）**—— a2a / middleware / team / evolution / channel / guardrail。
  使用者自担风险；在 v1.1 稳定前仍可能调整形态。
- **源码为准**—— 本页若与源码不一致，以源码为准。
  [`kairo-api/src/main/java/io/kairo/api/`](https://github.com/CaptainGreenskin/kairo/tree/main/kairo-api/src/main/java/io/kairo/api)

## 阅读约定

每条 SPI 都给出：签名、稳定性承诺、默认实现、用法示例、配置、生命周期、迁移策略、相关 ADR。
页面手维护，偏向指针保真而非长文；有疑问时点回源码。
