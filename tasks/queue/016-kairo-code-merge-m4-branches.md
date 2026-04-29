状态: DONE
创建时间: 2026-04-26
优先级: P0（M4 清理：合并未合并的 feature 分支）

## 目标

将已完成但未合并的 M4 feature 分支合并到 kairo-code main：
- feature/task-012-multi-provider（--provider openai/anthropic/qianwen）
- feature/task-014-output-file（--output 写文件）
- feature/task-015-token-usage（--show-usage token 统计）

## 执行步骤

1. git checkout main
2. git merge feature/task-012-multi-provider（可能与 014/015 有冲突，需解决）
3. git merge feature/task-014-output-file
4. git merge feature/task-015-token-usage
5. 解决所有冲突（主要在 KairoCodeMain.java）
6. mvn test -pl kairo-code-cli 全部通过

## 验收标准

- [ ] main 包含 --provider / --output / --show-usage
- [ ] 测试全部通过（115+ tests）

## Agent 可以自主完成

YES
