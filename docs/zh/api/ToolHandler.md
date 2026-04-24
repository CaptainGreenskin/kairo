# ToolHandler — API 参考

**Package:** `io.kairo.api.tool`
**稳定性:** `@Stable(since = "1.0.0")`——「工具执行契约；v0.1 起未变」
**源码:** [`ToolHandler.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/tool/ToolHandler.java)

执行一次具名工具调用。ReAct 循环按名字 + JSON Schema 校验后的 input map 挑工具，
分派到这里。返回 `ToolResult` 把 observation 喂回模型上下文。

## 签名

```java
public interface ToolHandler {
    ToolResult execute(Map<String, Object> input) throws Exception;
}
```

## 稳定性承诺

- `execute` 签名在 v1.x 内冻结。
- 可新增 default 方法，不删。

## 默认实现

| 实现 | 模块 | 备注 |
|------|------|------|
| `SearchTool` / `ReadFileTool` / `WriteFileTool` ... | `kairo-tools` | 附带 schema 的参考工具。 |
| 自定义 | 用户 | 实现此 SPI 暴露任意副作用 / 只读能力。 |

## 用法

```java
public final class SearchTool implements ToolHandler {
    @Override
    public ToolResult execute(Map<String, Object> input) {
        String q = (String) input.get("query");
        return ToolResult.ok(search(q));
    }
}
```

可运行示例：[`kairo-examples/.../quickstart/FullToolsetExample.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-examples/src/main/java/io/kairo/examples/quickstart/FullToolsetExample.java)。

## 配置

工具按名字 + 描述输入的 JSON Schema 注册到 `Agent`（或其 builder）。Schema 驱动模型侧的
工具选择；handler 在运行时侧校验。

## 生命周期

1. 实例化一次、注册到 agent。
2. `execute` 可能被反复、并发调用——实现必须线程安全。
3. 错误：可恢复的返回 `ToolResult.failure(...)`；致命的抛异常——映射为 `ToolExecutionException`。

## 迁移策略

`@Stable`——破坏式变更走 ADR + 主版本。

## 相关

- 指南：[异常映射](../guide/exception-mapping.md)
- SPI：[`ToolResult`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/tool/ToolResult.java)
- Guardrail：[ADR-007](../../adr/ADR-007-guardrail-spi-design.md)
