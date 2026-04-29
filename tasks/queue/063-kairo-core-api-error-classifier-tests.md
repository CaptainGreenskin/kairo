状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：API 错误分类测试）

## 目标

为 `ApiErrorClassifierImpl` 添加单元测试，验证各类 API 错误
消息被正确分类为对应的 `ApiErrorType`。

## 背景

`ApiErrorClassifierImpl` 将模型 API 抛出的异常分类为
PROMPT_TOO_LONG / MAX_OUTPUT_TOKENS / RATE_LIMITED / SERVER_ERROR /
AUTHENTICATION_ERROR / UNKNOWN，并提取 retry-after 信息。

## 需要实现

先读取 `ApiErrorClassifierImpl.java` 和 `ApiErrorType` 枚举，然后编写：

### 测试：ApiErrorClassifierImplTest.java

验证：
- "prompt is too long" → PROMPT_TOO_LONG
- "context_length_exceeded" → PROMPT_TOO_LONG
- "max_tokens" → MAX_OUTPUT_TOKENS
- "rate limit" / "429" → RATE_LIMITED
- "retry after 30" → RATE_LIMITED with Duration.ofSeconds(30)
- "500 internal server error" / "overloaded" → SERVER_ERROR
- "401 unauthorized" / "invalid api key" → AUTHENTICATION_ERROR
- 已是 ApiException 时直接返回其 errorType
- 未知错误 → UNKNOWN

## 验收标准

- [ ] 8+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
