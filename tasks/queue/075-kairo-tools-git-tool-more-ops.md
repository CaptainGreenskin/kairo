状态: DONE
创建时间: 2026-04-27
优先级: P2（M3：测试补全）

## 目标

为 `GitTool` 补充 add、commit、branch、checkout、diff-staged 等操作的测试。

## 背景

现有 `GitToolTest` 只覆盖了 8 个场景（status、log、diff、危险操作拦截）。
add/commit 是最常用的 git 操作，缺乏测试覆盖。

## 需要实现

在 `GitToolTest.java` 中增加（或新建 `GitToolExtendedTest.java`）：

- `git add .` 后 status 变为已暂存
- `git add` + `git commit -m "msg"` 成功提交
- `git branch new-branch` 创建分支成功
- `git checkout -b feature` 创建并切换分支
- `git diff --staged` 显示暂存的 diff
- `git log --oneline` 返回提交记录
- `git status --porcelain` 机器可读格式

## 验收标准

- [ ] 7+ 新测试通过
- [ ] `mvn test -pl kairo-tools` 通过

## Agent 可以自主完成

YES
