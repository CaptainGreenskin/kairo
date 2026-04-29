状态: DONE
创建时间: 2026-04-26
优先级: P0（M5 清理：合并未合并的 feature 分支）

## 目标

将已完成但未合并的 feature 分支合并到 kairo-code main：
- feature/task-017-parallel-tasks（--task-list 并行任务）
- feature/task-018-repl-usage-cmd（:usage 命令）
- feature/task-019-post-reasoning-usage（AgentEventPrinter token 用量）
- feature/task-020-config-file（~/.kairo-code/config.properties）
- feature/task-021-m5-self-improvement（system-prompt + 能力测试）

## 执行步骤

1. git checkout main
2. 逐个 merge，解决冲突（主要在 KairoCodeMain.java）
3. mvn test -pl kairo-code-cli kairo-code-core 全部通过

## 验收标准

- [ ] main 包含所有 M4/M5 功能
- [ ] 测试全部通过

## Agent 可以自主完成

YES
