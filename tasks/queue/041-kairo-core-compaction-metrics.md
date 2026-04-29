状态: DONE
创建时间: 2026-04-26
优先级: P3（M6：上下文压缩可观测性）

## 目标

在 `ContextCompactionEngine` 中添加压缩次数计数器，通过 PostCompact hook
事件暴露压缩统计（stage、tokens_before、tokens_after）。

## 背景

当前压缩事件发生后没有可观测的指标。运维人员无法知道：
1. 哪个 stage 被触发
2. 压缩了多少 token
3. 整个会话中压缩了多少次

## 需要实现

### 1. PostCompactEvent 中增加字段

检查 `PostCompactEvent`（在 kairo-api 中）是否有 tokensAfter 字段。
若无，在 kairo-core 侧的压缩流程中通过 DefaultHookChain 直接构建事件，
使用现有 PostCompactEvent 结构（不修改 kairo-api）。

### 2. ContextCompactionEngine.java

在每次触发压缩后，构建并 fire `PostCompact` hook event，
包含压缩前/后 token 数（或消息数）。

### 3. 测试：CompactionMetricsHookTest.java（3+ 用例）

验证 PostCompact hook 被触发，事件包含压缩信息。

## 验收标准

- [ ] 压缩完成后 PostCompact hook 被触发
- [ ] 事件包含可量化指标
- [ ] 3+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES（若 PostCompactEvent 在 kairo-api 需要新字段，标记 NEEDS_HUMAN_REVIEW，
改为仅写测试验证现有触发逻辑）
