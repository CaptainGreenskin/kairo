状态: DONE
创建时间: 2026-04-26
优先级: P2（M6 准备：熔断器改进）

## 目标

改进 kairo-core 的 CircuitBreaker：支持可配置的恢复窗口（recovery window），
让熔断器在等待 N 秒后自动进入 HALF_OPEN 状态尝试恢复，而不是永久 OPEN。

## 背景

当前 CircuitBreaker 在触发后进入 OPEN 状态，但恢复逻辑不完整。
M6 中 Agent 需要处理模型短暂不可用的情况，需要自动恢复能力。

## 需要实现

### 1. 读取 CircuitBreaker 当前实现

路径：`kairo-core/src/main/java/io/kairo/core/resilience/CircuitBreaker.java`（如存在）

### 2. 添加 HALF_OPEN 状态转换

- OPEN → HALF_OPEN：等待 recoveryWindowMs 毫秒后自动转换
- HALF_OPEN → CLOSED：一次成功调用后关闭
- HALF_OPEN → OPEN：一次失败后重新打开

### 3. 测试：CircuitBreakerRecoveryTest.java（3+ 用例）

## 验收标准

- [ ] HALF_OPEN 状态转换正确
- [ ] 3+ 测试通过
- [ ] mvn test -pl kairo-core 通过

## Agent 可以自主完成

YES（只改 kairo-core，不涉及 kairo-api SPI）
