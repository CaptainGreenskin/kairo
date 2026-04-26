# ADR-010: ToolResultBudget Contract for v0.7.1 to v0.8

- Status: Accepted
- Date: 2026-04-23
- Deciders: Kairo Core Runtime Maintainers

## Context

工具输出可能远大于模型剩余上下文预算，导致：

- 后续推理轮次在高压上下文下抖动；
- 压缩策略被动触发，降低关键信息保留率；
- replay/排障难以判断结果被裁剪与否；
- `v0.8` 执行事件模型无法稳定消费 `v0.7.1` 的工具结果行为。

## Decision

在 `v0.7.1` 引入 ToolResultBudget 契约，并作为 `v0.8` 的兼容基础：

1. 工具结果进入 history 前统一经过预算治理。
2. 对单条结果执行裁剪时，保留尾注说明与原始/保留 token 统计。
3. 在 TOOL 消息写入预算元数据，作为执行回放和审计输入。
4. 对工具执行路径补充结构化错误分类字段。

## Contract

### Tool message metadata (required)

- `tool_result_budget_applied`
- `tool_result_budget_truncated_count`
- `tool_result_budget_original_tokens`
- `tool_result_budget_kept_tokens`
- `tool_result_budget_remaining_tokens`
- `tool_result_budget_per_result_tokens`

### ToolResult metadata (required)

- `tool_result_budget_applied`
- `tool_result_original_tokens`
- `tool_result_kept_tokens`
- `tool_result_truncated`
- `tool_result_budget_reason`

### Error/result classification (required)

- `error_code=tool_execution_failed`
- `error_code=tool_blocked_by_hook`
- `error_code=tool_cancelled_by_hook`
- `result_code=tool_skipped_by_hook`

## Consequences

### Positive

- 大结果不再无上限占用上下文。
- 可观测性字段可直接进入 replay 与审计流水。
- `v0.8` 可以直接复用 `v0.7.1` 输出结构。

### Trade-offs

- 极端场景下，工具结果会被截断，完整输出需依赖工具侧持久化或外部日志。
- 裁剪阈值为启发式策略，后续可能按模型/工具类型细分。

## Follow-up

- 在 `v0.8` 执行事件流中把预算字段提升为一级事件属性。
- 在 dashboard 中新增“tool result truncated rate”指标。
