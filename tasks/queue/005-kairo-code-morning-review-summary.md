状态: DONE
创建时间: 2026-04-26
优先级: P1（支持早上批量 review 工作流）

## 目标

添加 `tasks/review.sh` 脚本，扫描昨晚的执行日志，生成 Markdown 格式的
早上 review 摘要，打印到 stdout。

## 上下文

- 这是一个 shell 脚本，不是 Java 代码
- 依赖任务 002 产生的日志文件（`KAIRO_CODE_LOG_DIR` 下）
- 放置位置：`/Users/liulihan/IdeaProjects/sre/claude/kairo/tasks/review.sh`

## 需要实现

脚本功能：
1. 读取 `KAIRO_CODE_LOG_DIR`（默认 `./logs`）下最近 24 小时的日志文件
2. 统计：成功数/失败数/超时数
3. 列出每个任务：状态 + 任务来源 + 执行时长 + 简短摘要
4. 失败任务高亮显示（用 `[FAILED]` 前缀）
5. 最后输出：需要 review 的 feature 分支列表（`git branch -r | grep feature/`）

输出示例：
```
# 夜间执行摘要 2026-04-26

成功: 3 | 失败: 1 | 超时: 0

## 任务详情
✅ [02:30 ~ 02:47] tasks/queue/001-xxx.md (17m)
   → 添加了 --task-file 选项，3 个测试通过

❌ [02:48 ~ 03:15] tasks/queue/003-xxx.md (27m)
   → 需要修改 kairo-api，已标记 NEEDS_HUMAN_REVIEW

✅ [03:16 ~ 03:44] tasks/queue/004-xxx.md (28m)
   → 新增 3 个 IT 场景，全部通过

✅ [03:45 ~ 04:12] tasks/queue/005-xxx.md (27m)
   → 生成 review.sh 脚本

## 待 Review 的分支
  feature/task-001-kairo-code-task-file-input
  feature/task-004-kairo-evolution-e2e-integration-test
  feature/task-005-kairo-code-morning-review-summary
```

## 验收标准

- [ ] `bash tasks/review.sh` 在有日志文件时输出摘要
- [ ] 无日志文件时输出 "昨晚没有执行记录"
- [ ] 脚本有 execute 权限（chmod +x）
- [ ] 在 macOS zsh 环境下可运行

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES

---
## 完成记录
- 时间：2026-04-26
- 改动：新增 tasks/review.sh（71 行）
- 测试：macOS zsh 下手动验证通过，chmod +x 已设置
