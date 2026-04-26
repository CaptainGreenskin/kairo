# Msg — API 参考

**Package:** `io.kairo.api.message`
**稳定性:** `@Stable(since = "1.0.0")`——「核心消息类型；v0.1 起形态冻结」
**源码:** [`Msg.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/message/Msg.java)

各组件共享的中立消息载体：agent 输入、模型输出、工具调用、工具观察。一个 role
（`user` / `assistant` / `system` / `tool`）、文本内容、可选工具调用、可选 metadata——
provider 特有形态不会跨边界泄漏。

## 签名（摘要）

```java
public class Msg {
    public static Msg user(String content);
    public static Msg assistant(String content);
    public static Msg system(String content);
    public static Msg tool(String toolName, String result);

    public String role();
    public String content();
    public List<ToolCall> toolCalls();
    public Map<String, Object> metadata();
}
```

## 稳定性承诺

- v1.x 内工厂方法签名与 getter 名字冻结。
- 可新增工厂 / default 访问器。
- 序列化字段顺序不属于契约。

## 默认实现

`Msg` 本身是具体、约定不可变的类型——不做多态设计。Provider 在 `ProviderPipeline`
的 request-builder / response-parser 阶段把 `Msg` 转 provider 线格式、再回转。

## 用法

```java
Msg reply = agent.call(Msg.user("起草一条 changelog")).block();
System.out.println(reply.content());
```

## 配置

会话级 metadata（session id / trace id / user id）挂在 `Msg.metadata()` 上。provider
应把 metadata 复制到请求载荷用于可观测关联，但不得依赖任何特定 key。

## 生命周期

1. 由调用方或 agent step 产生（`AgentState` 保留由 `ContextManager` 管理）。
2. 流经 `ReActLoop`——model call → tool dispatch → observation → next model call。
3. 循环终止时落定；保留到 `AgentState` 供后续回合使用。

## 迁移策略

`@Stable`——新增字段增量式，不做破坏式变更。任何变更走 ADR + japicmp。

## 相关

- SPI：[`ToolCall`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/message/ToolCall.java)
- 上下文：[`ContextManager` / 压缩流水线](../../adr/ADR-006-compaction-pipeline-architecture.md)
