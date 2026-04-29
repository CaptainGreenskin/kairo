状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：重试策略测试）

## 目标

为 `ProviderRetry` 添加单元测试，验证模型 provider 调用失败时
的指数退避重试策略。

## 背景

`ProviderRetry` 封装了对 `ModelProvider.call()` 的重试逻辑，
在遇到可重试错误时使用指数退避。这是 agent 在网络抖动场景下
的可靠性保障。

## 需要实现

先读取 `ProviderRetry.java` 理解接口，然后编写：

### 测试：ProviderRetryTest.java

验证：
- 第一次调用成功时不重试
- 可重试错误（如 rate limit）触发重试
- 达到最大重试次数后返回 error
- 不可重试错误（如 401）立即失败不重试

## 验收标准

- [ ] 4+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
