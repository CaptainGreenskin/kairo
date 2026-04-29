状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Hook 顺序正确性）

## 目标

为 `DefaultReActAgent` 的错误路径添加集成测试，验证 OnError hook 在
SessionEnd hook 之前触发（顺序：OnError → OnSessionEnd → propagate error）。

## 背景

任务 034 实现了 OnError hook 触发，但没有测试 Hook 触发顺序。
在监控场景下，OnError 必须在 SessionEnd 前触发，才能让错误处理器在会话结束前做标记。

## 需要实现

### 1. 测试：OnErrorBeforeSessionEndTest.java（3+ 用例）

使用 `DefaultHookChain` + 模拟 `DefaultReActAgent` 行为（或直接通过 `AgentBuilder`
构建一个会失败的 Agent），验证：
- OnError hook 在 OnSessionEnd hook 之前触发
- 两个 hook 都被触发
- 原始异常被保留并从 Agent 抛出

不需要真实模型调用，可以注入一个立即抛出异常的 ToolExecutor。

## 验收标准

- [ ] 触发顺序正确：OnError → OnSessionEnd
- [ ] 3+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES（只写测试，不改实现；若发现顺序错误则同时修复实现）
