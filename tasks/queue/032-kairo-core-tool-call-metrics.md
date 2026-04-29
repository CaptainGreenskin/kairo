状态: DONE
创建时间: 2026-04-26
优先级: P2（M6 准备：工具调用监控）

## 目标

在 kairo-core 的执行上下文中添加工具调用计数器，
让 AgentSnapshot 能返回 per-tool 调用次数统计（toolCallCounts）。

## 背景

M6 需要了解 Agent 在执行任务时使用了哪些工具、各用了多少次，
这对优化 Agent 行为和 prompt 工程非常重要。

## 需要实现

### 1. 检查 AgentSnapshot 是否可以扩展

AgentSnapshot 在 kairo-api，不能改 SPI。
改为在 DefaultReActAgent 的 snapshot() 实现中，通过注释说明当前 toolCallCounts 不在 SPI 中，
只在 kairo-code 侧通过 POST_ACTING hook 统计。

### 2. 在 kairo-code AgentEventPrinter 中添加工具调用统计

维护 Map<String, Integer> toolCallCounts，在 onPostActing 中更新，
在 SESSION_END 或 `:usage` 命令中汇报。

### 3. 修改 UsageCommand 也显示 top-3 工具调用

### 4. 测试：ToolCallMetricsTest.java（3+ 用例）

## 验收标准

- [ ] AgentEventPrinter 统计 toolCallCounts
- [ ] UsageCommand 显示 top-3 工具
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES（只改 kairo-code-cli，不涉及 kairo-api SPI）
