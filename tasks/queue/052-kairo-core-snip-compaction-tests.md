状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：压缩策略测试）

## 目标

为 `SnipCompaction` 添加单元测试，验证 Level-1 压缩（剪裁旧工具结果）
的触发条件和压缩行为。

## 背景

`SnipCompaction` 是 6 阶段压缩引擎的第一级，在 80% 压力时触发，
将旧的 tool result 内容替换为占位符。目前缺少专门测试。

## 需要实现

### 测试：SnipCompactionTest.java

验证：
- `shouldTrigger()` 在压力低于阈值时返回 false
- `shouldTrigger()` 在压力高于阈值时返回 true
- `compact()` 对空消息列表返回原列表不崩溃
- `compact()` 保留最近 N 条 tool result 不压缩
- `compact()` 将旧 tool result 内容替换为占位符
- 压缩后消息数量不变（只替换内容，不删除消息）

使用 `ContextState` 构建压力值，`CompactionConfig` 用
`CompactionConfig.defaults()` 或 builder。

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
