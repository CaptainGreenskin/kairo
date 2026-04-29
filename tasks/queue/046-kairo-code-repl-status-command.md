状态: NEEDS_HUMAN_REVIEW
创建时间: 2026-04-26
优先级: P3（M6：kairo-code 可观测性）

## 目标

在 kairo-code REPL 中添加 `/status` 命令，显示当前 Agent 状态：
iteration 数、token 用量、活跃工具数、会话时长。

## 背景

当前 REPL 没有内省命令可查看 Agent 运行状态。
`/usage` 命令只显示历史累计，需要一个实时快照命令。

## 需要实现

### 1. StatusCommand.java（kairo-code-cli）

实现 `/status` 命令，从 `AgentSnapshot` 读取并展示：
```
Agent: <name>  State: RUNNING
Iteration: 3   Tokens: 12,450
Total tool calls: 7
Session time: 00:02:31
```

### 2. 注册到 CommandRegistry

在 `ReplLoop.createCommandRegistry()` 中注册。

### 3. 测试：StatusCommandTest.java（3+ 用例）

## 验收标准

- [ ] `/status` 命令输出包含 state、iteration、tokens
- [ ] 3+ 测试通过
- [ ] `mvn test -pl kairo-code-cli` 通过

## Agent 可以自主完成

YES
