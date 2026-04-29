状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Level-3 压缩策略测试）

## 目标

为 `CollapseCompaction` 添加单元测试，验证 Level-3 压缩
（连续 tool call 消息组折叠）的触发和压缩行为。

## 背景

`CollapseCompaction` 在 90% 压力时触发，将连续的
ASSISTANT(ToolUse) + TOOL(ToolResult) 消息组（≥3条）
合并为单条 `[Collapsed: N tool calls (read x3, write x2) - all successful]`。

## 需要实现

先读取 `CollapseCompaction.java`，然后编写：

### 测试：CollapseCompactionTest.java

验证：
- `shouldTrigger` 行为（高/低压力）
- 消息数 < MIN_GROUP_SIZE(3) 时不折叠
- 连续 3+ tool 相关消息被折叠为单条
- 折叠摘要包含工具名和调用次数
- 全部成功时显示 "all successful"，有错误时显示错误计数
- 非 tool 消息不被折叠
- `name()` 返回 "collapse"，`priority()` 返回 300

## 验收标准

- [ ] 6+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
