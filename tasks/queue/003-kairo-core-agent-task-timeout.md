状态: NEEDS_HUMAN_REVIEW
创建时间: 2026-04-26
优先级: P1（防止夜间任务无限运行）

## 目标

在 `AgentConfig` 中添加 `maxTaskDuration` 全局任务超时配置，
防止单个任务在夜间无限执行。与现有的 `maxIterations` 互补。

## 上下文

- 相关模块：kairo-core
- 现有约束：`maxIterations`（最大轮次）存在，但没有时间维度的限制
- 相关文件：
  - `kairo-core/src/main/java/io/kairo/core/agent/AgentConfigDefaults.java`
  - `kairo-core/src/main/java/io/kairo/core/agent/DefaultReActAgent.java`
  - `kairo-core/src/main/java/io/kairo/core/agent/AgentLifecycleManager.java`

## 需要实现

1. 在 `AgentConfig` 添加 `maxTaskDuration()` 方法，返回 `Optional<Duration>`
2. 默认值：30 分钟（`AgentConfigDefaults` 中设置）
3. 在 `DefaultReActAgent` 的执行循环中，通过 Reactor `timeout()` 操作符强制执行
4. 超时时抛出 `KairoException`（子类），包含已执行轮次和已耗时信息
5. 超时异常触发 `OnError` Hook

## 验收标准

- [ ] `AgentConfig.builder().maxTaskDuration(Duration.ofMinutes(5))` 可设置
- [ ] 单元测试：任务超时后正确抛出异常
- [ ] 单元测试：默认 30 分钟生效
- [ ] 单元测试：设置 `Optional.empty()` 时不超时（无限制模式）
- [ ] 不破坏现有 97 个 kairo-core 测试
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

NO — 需要在 kairo-api 的 AgentConfig 添加方法，请标记为 NEEDS_HUMAN_REVIEW 并在 PR 描述说明

---
## 完成记录
