状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Token 预算管理测试）

## 目标

为 `TokenBudgetManager` 添加单元测试，验证 token 压力计算和预算耗尽判断。

## 背景

`TokenBudgetManager` 是上下文压缩触发的核心，需要确保压力系数、
耗尽条件、不同模型默认值的正确性。

## 需要实现

### 测试：TokenBudgetManagerTest.java（5+ 用例）

验证：
- token 使用率低于阈值时 `isExhausted()` 返回 false
- token 使用率超过阈值时 `isExhausted()` 返回 true
- `pressure()` 在 [0.0, 1.0] 范围内
- 不同模型上下文窗口的 forModel() 工厂方法正确
- 超过 budget 后压力为 1.0

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
