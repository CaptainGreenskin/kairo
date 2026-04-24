# Agent — API 参考

**Package:** `io.kairo.api.agent`
**稳定性:** `@Stable(since = "1.0.0")`——「核心 ReAct 契约；v0.1 起未变」
**源码:** [`Agent.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/agent/Agent.java)

Kairo 的可运行单元。接收 `Msg`，在配置的 `ModelProvider` 与工具之间跑 ReAct 循环
（Thought → Action → Observation），并在反应式边界上返回终态 `Msg`。

## 签名

```java
public interface Agent {
    Mono<Msg> call(Msg input);
    String id();
    String name();
    AgentState state();
}
```

## 稳定性承诺

- v1.x 内二进制兼容。
- 可新增 `default` 方法。
- 删除 `call(Msg)` / `id()` / `name()` / `state()` 需主版本 bump。

## 默认实现

| 实现 | 模块 | 备注 |
|------|------|------|
| `DefaultReActAgent` | `kairo-core` | 生产级 ReAct 循环，含工具调度、上下文压缩、Hook。 |
| 自定义 | 用户 | 实现 `Agent` 以绕开默认循环（如单轮 LLM 调用）。 |

## 用法

```java
Agent agent = DefaultReActAgent.builder()
    .modelProvider(new AnthropicProvider(apiKey))
    .registerTool(new SearchTool())
    .build();

Msg reply = agent.call(Msg.user("总结今天的 PR")).block();
```

可运行示例：[`kairo-examples/.../quickstart/AgentExample.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-examples/src/main/java/io/kairo/examples/quickstart/AgentExample.java)。

## 生命周期

1. 每 session / 会话实例化一次（通常通过 builder）。
2. `call(Msg)` 每回合调用，跑完整 ReAct 循环。
3. 默认实现线程安全；自定义实现自证自说。

## 迁移策略

`@Stable`——破坏式变更需 ADR + japicmp（见 `docs/governance/japicmp-policy.md`），先弃用再移除，跨大版本生效。

## 相关

- ADR：[ADR-001 — ReAct 循环拆解](../../adr/ADR-001-react-loop-decomposition.md)
- Census：[`spi-census-v1.0.md` → agent](../../governance/spi-census-v1.0.md)
