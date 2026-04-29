状态: DONE
创建时间: 2026-04-26
优先级: P2（M5 UX：单次模式流式输出）

## 目标

在 `--task` 单次模式中支持流式输出，让用户能实时看到 Agent 的思考过程和最终响应，
而不是等待全部完成后一次性打印。

## 背景

当前单次模式用 `agent.call(msg).block()` 阻塞等待，用于长任务时用户没有反馈。
REPL 模式已有 `StreamingAgentRunner` 支持流式输出。

## 需要实现

### 1. 在 runOneShot 中可选使用流式

当 `--verbose` 时，使用 `StreamingAgentRunner` 进行流式输出。
非 verbose 时，保持原有的 block() 方式。

### 2. 与 --output 兼容

流式输出时，仍将最终响应写入 `--output` 指定的文件。

### 3. 测试：OneShotStreamingTest.java（3+ 用例）

## 验收标准

- [ ] --verbose --task "..." 流式输出响应片段
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES
