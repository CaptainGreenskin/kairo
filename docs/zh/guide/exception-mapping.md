# 异常映射参考

Kairo 在内部实现异常与公开 API 异常层级之间保持干净的边界。所有面向消费者的异常都继承自
`io.kairo.api.exception.KairoException`。

## 异常层级

```
KairoException (kairo-api)
├── AgentException
│   ├── AgentExecutionException
│   └── AgentInterruptedException
├── ModelException
│   ├── ModelApiException          (v0.6 新增)
│   ├── ModelRateLimitException
│   ├── ModelTimeoutException
│   └── ModelUnavailableException
│       └── CircuitBreakerOpenException
├── ToolException
│   ├── ToolPermissionException
│   └── PlanModeViolationException
└── MemoryStoreException           (v0.6 新增)
```

## 内部 → 公开 映射表

| 内部异常（kairo-core） | 公开异常（kairo-api） | 可重试 | 上下文 |
|------------------------|------------------------|--------|--------|
| `ModelProviderException.RateLimitException` | `ModelRateLimitException` | 是 | HTTP 429，触发限流 |
| `ModelProviderException.ApiException` | `ModelApiException` | 否（临时性除外） | 非 200 HTTP 或响应解析失败 |
| `CircuitBreakerOpenException` | `ModelUnavailableException` | 是（冷却后） | 熔断器 open 态 |
| `JdbcMemoryStore` 存储错误 | `MemoryStoreException` | 否 | SQL / 连接失败 |
| `LoopDetectionException` | `AgentExecutionException` | 否 | 检测到工具调用死循环 |

## 映射策略

异常映射在**反应式边界**上用 `onErrorMap` 集中处理，不在单个 throw 点分散。这样：

- 内部重试逻辑（`ProviderRetry`）看到的是原始异常类型，分类准确。
- 只有最终、未重试成功的错误才映射到公开 API 类型。
- 每个公开方法只有一个映射点，不会漏抛。

```java
// 所有 provider 的通用模式：
return Mono.defer(() -> { /* 内部逻辑抛内部异常 */ })
    .transform(ProviderRetry.withConfigPolicy(...))    // 重试看到原类型
    .onErrorMap(ExceptionMapper::toApiException);       // 边界统一映射
```

## 结构化字段（Phase B 规划）

未来版本会给 `KairoException` 加上结构化字段：

- `error.code`——机器可读错误码
- `error.category`——错误分类
- `retryable`——是否可重试
- `retry.after.ms`——建议重试延迟（毫秒）
