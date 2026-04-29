状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：错误恢复健壮性）

## 目标

为 `ReActLoop.recoverDanglingToolCalls()` 补充边界测试用例，
验证各种 history 状态下的恢复行为。

## 背景

`DanglingToolCallRecoveryTest.java` 已存在，但可能缺少：
- 多个并发 tool_use 只有部分有 result 的情况
- 空 history 的情况
- 最后消息不是 ASSISTANT 的情况
- tool_use 有对应 TOOL result 的情况（不应重复注入）

## 需要实现

在 `DanglingToolCallRecoveryTest.java` 中补充（或新建独立测试类）：
- partiallyAnswered: 3 个 tool_use，只有 1 个有 result → 注入 2 个 error result
- noHistory: 空 history 不崩溃
- lastMessageNotAssistant: 最后是 USER 消息，无需恢复
- allAnswered: 全部有 result，不注入额外消息

## 验收标准

- [ ] 4+ 测试用例
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
