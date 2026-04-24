# ModelProvider — API 参考

**Package:** `io.kairo.api.model`
**稳定性:** `@Stable(since = "1.0.0")`——「核心模型调用契约；v0.1 起未变」
**源码:** [`ModelProvider.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/model/ModelProvider.java)

对 LLM 后端的抽象。实现者负责包装 provider 的 REST / 流式 API，适配到 Kairo 反应式
`Mono<ModelResponse>` / `Flux<ModelResponse>` 契约。Provider 通过四阶段
[`ProviderPipeline`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/model/ProviderPipeline.java)
（request builder / response parser / stream subscriber / error classifier）支持深度定制。

## 签名

```java
public interface ModelProvider {
    Mono<ModelResponse> call(List<Msg> messages, ModelConfig config);
    Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config);
    String name();
}
```

## 稳定性承诺

- v1.x 内二进制兼容。
- 可新增 `default` 方法。
- `call` / `stream` / `name` 签名在该主版本内冻结。

## 默认实现

| 实现 | 模块 | 备注 |
|------|------|------|
| `AnthropicProvider` | `kairo-core` | Claude / Messages API；实现 `ProviderPipeline`。 |
| `OpenAIProvider` | `kairo-core` | Chat Completions API；实现 `ProviderPipeline`。 |
| `MockModelProvider` | `kairo-examples` | 确定性测试 double。 |

## 用法

```java
ModelProvider provider = new AnthropicProvider(System.getenv("ANTHROPIC_API_KEY"));
ModelResponse resp = provider.call(
    List.of(Msg.user("Hello")),
    ModelConfig.builder().model("claude-opus-4-7").build())
    .block();
```

## 配置

模型选型、token / temperature / 工具定义写在 `ModelConfig` 上。
Provider 特有的 base URL / 超时 / 重试走各自 builder。

## 生命周期

1. 每后端一实例（builder 创建）。
2. 线程安全；跨请求并发调用。
3. `stream()` 返回冷 Flux——每个订阅者触发一次独立请求。

## 迁移策略

`@Stable` 面；破坏式变更需 ADR + japicmp 批准，跨大版本生效。

## 相关

- ADR：[ADR-005 — Provider 拆解模板](../../adr/ADR-005-provider-decomposition-template.md)
- SPI：[ProviderPipeline](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/model/ProviderPipeline.java)
