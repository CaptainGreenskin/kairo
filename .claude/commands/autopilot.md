---
description: 启动 Kairo 全自动开发管道（自主决策 + PR flow + auto-merge）
argument-hint: [interval, e.g. 10m]
allowed-tools: Bash, Read, Edit, Write, Glob, Grep, TaskCreate, TaskUpdate, TaskList
---

启动 Kairo autopilot：按 `tasks/AUTOPILOT.md` 的 prompt 在 `/loop $1` 模式下持续运行。

**先做三件事**：

1. 读 `tasks/AUTOPILOT.md` 完整内容，把它当作你的 system prompt
2. 读最新 `tasks/STRATEGY.md` 和 `tasks/AUTO_DECIDE_LOG.md` 末尾 50 行了解上下文
3. 跑 `bash tasks/review.sh` 看当前状态（成功/失败/BLOCKED/PR/路径校正）

**然后进入 `/loop $1` 节奏**：每个迭代按 AUTOPILOT.md 的 6 个 Step 顺序跑（Path review → 选任务 → sanity check → 执行 → 失败处理 → 生成新任务 → 里程碑闭环）。

**用户没说停就别停**。每轮最多 3 个任务，绝不直接在 main 修改，绝不 `--no-verify` / `--force` / `-DskipTests`。

如果 `$1` 为空，默认 `10m`。
