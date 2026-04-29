状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Agent 指标）

## 目标

在 `AgentSnapshot` 中添加 `totalToolCalls()` 字段，记录整个 Agent
生命周期内的工具调用总次数。

## 背景

目前 `AgentSnapshot` 只有 iterations/tokenUsage 等字段。
工具调用次数是运维监控的关键指标，缺失会导致无法区分"推理密集"和"工具密集"的 Agent。

## 需要实现

### 1. 检查 AgentSnapshot 当前字段

查看 `kairo-api/.../agent/AgentSnapshot.java` 的现有字段。
如果是 record 类型且在 kairo-api 中，则**不修改 SPI**，
而是在 DefaultReActAgent 的统计逻辑中用 AtomicInteger 维护计数器，
通过 `AgentSnapshot` 的构建路径注入（如果接口允许）。

### 2. DefaultReActAgent 或 ToolPhase 中维护计数

在 ToolPhase 每次成功 invoke 后递增计数器，
在 `DefaultReActAgent.snapshot()` 中包含进去。

### 3. 测试：AgentToolCallCountTest.java（3+ 用例）

验证 snapshot 中 totalToolCalls 递增。

## 验收标准

- [ ] `AgentSnapshot` 可通过某个路径访问到 totalToolCalls（或等价字段）
- [ ] 3+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES（只改 kairo-core；若 AgentSnapshot 在 kairo-api 且为 record，标记 NEEDS_HUMAN_REVIEW）
