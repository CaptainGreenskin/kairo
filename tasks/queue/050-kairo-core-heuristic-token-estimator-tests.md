状态: DONE
创建时间: 2026-04-26
优先级: P3（M6：Token 估算精度）

## 目标

为 `HeuristicTokenEstimator` 添加单元测试，验证 chars*4/3 估算逻辑。

## 背景

`HeuristicTokenEstimator` 是上下文压缩的后备 token 估算器，
使用 `chars * 4 / 3` 公式。逻辑简单但影响压缩触发时机，
需要确保公式正确且对边界情况（空消息、null text）不崩溃。

## 需要实现

### 测试：HeuristicTokenEstimatorTest.java

验证：
- 空列表返回 0
- 单条消息：chars * 4 / 3（整数除法）
- 多条消息：所有 chars 之和 * 4 / 3
- 消息 text 为空字符串时不崩溃
- 结果始终 >= 0

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
