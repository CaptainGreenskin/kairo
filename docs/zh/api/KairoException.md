# KairoException — API 参考

**Package:** `io.kairo.api.exception`
**稳定性:** `@Stable(since = "1.0.0")`——「结构化错误字段的根异常」
**源码:** [`KairoException.java`](https://github.com/CaptainGreenskin/kairo/blob/main/kairo-api/src/main/java/io/kairo/api/exception/KairoException.java)

Kairo 异常层级的根。所有可恢复 / 可分类的失败都继承这里；类本身携带结构化错误元数据
（code / category / retryability），让调用方无需字符串匹配即可分支处理。

## 签名（摘要）

```java
public class KairoException extends RuntimeException {
    public KairoException(String message);
    public KairoException(String message, Throwable cause);

    public String errorCode();
    public ErrorCategory errorCategory();
    public boolean retryable();
    public Map<String, Object> attributes();
}
```

## 稳定性承诺

- v1.x 内类名、package、构造签名、getter 名冻结。
- 可新增结构化属性字段，不删。
- 子类型在 `io.kairo.api.exception.*` 下，各自带稳定性注解。

## 子类型速查

| 子类型 | 职责 |
|--------|------|
| `ToolExecutionException` | 工具 handler 抛异常或产生致命结果。 |
| `ModelInvocationException` | Provider 侧失败（HTTP / 解析 / 限流）。 |
| `ModelUnavailableException` | Provider 暂不可达——通常可重试。 |
| `GuardrailException` | Guardrail 拦截了请求 / 响应。 |
| `MiddlewareRejectException` | Middleware 短路拒绝请求。 |
| `ContextOverflowException` | 压缩后上下文仍超 token 预算。 |

完整层级与反应式边界映射契约见 `docs/zh/guide/exception-mapping.md`。

## 用法

```java
try {
    agent.call(Msg.user(input)).block();
} catch (KairoException ex) {
    if (ex.retryable()) {
        schedule(ex);
    } else {
        log.error("致命：code={}, category={}", ex.errorCode(), ex.errorCategory(), ex);
    }
}
```

## 迁移策略

`@Stable`——新增子类型增量式；保留根类是 v1.x 硬承诺。变更需 ADR + japicmp 批准。

## 相关

- ADR：[ADR-004 — 异常层级设计](../../adr/ADR-004-exception-hierarchy-design.md)
- ADR：[ADR-008 — 结构化错误字段](../../adr/ADR-008-exception-phase-b-structured-fields.md)
- 指南：[`exception-mapping.md`](../guide/exception-mapping.md)
