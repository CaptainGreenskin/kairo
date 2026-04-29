状态: DONE
创建时间: 2026-04-26
优先级: P2（M6 准备：超时 Hook）

## 目标

在 kairo-core 中，当 Agent 因超时终止时，触发 OnError hook，
让监控系统能捕获超时事件并记录日志。

## 背景

当前超时通过 Reactor `timeout()` 直接抛出 TimeoutException，
但 OnError hook 没有被调用，导致超时无法被 Hook 监控。

## 需要实现

### 1. 在 DefaultReActAgent 中处理 TimeoutException

在 `call()` 方法的 `onErrorResume` 中，当异常是 TimeoutException 时，
触发 OnError hook（如果已注册）。

### 2. 测试：AgentTimeoutHookTest.java（3+ 用例）

验证：
- 超时时 OnError hook 被调用
- hook 中能访问到异常类型
- 非超时错误也触发 hook

## 验收标准

- [ ] 超时触发 OnError hook
- [ ] 3+ 测试通过
- [ ] mvn test -pl kairo-core 通过

## Agent 可以自主完成

YES（只改 kairo-core 实现，不改 kairo-api SPI）
