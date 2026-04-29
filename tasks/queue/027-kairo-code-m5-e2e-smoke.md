状态: DONE
创建时间: 2026-04-26
优先级: P1（M5 核心验证：自我修改端到端）

## 目标

创建一个 M5 端到端验证任务：kairo-code 修改自身代码库中的一个已知简单位置，
并通过 mvn test 验证修改正确。

## 背景

M5 里程碑的完成标志是"kairo-code 能修改自身代码并通过测试"。
task-021 验证了工具和系统提示已就绪。此任务创建一个具体的验证脚本。

## 需要实现

### 1. 创建验证脚本 scripts/m5-self-mod-smoke.sh

```bash
#!/bin/bash
# M5 smoke test: kairo-code modifies itself
# 1. Target: add a comment to ConfigLoader.java
# 2. Verify: mvn test passes
# 3. Revert: git checkout -- the file
```

### 2. M5SmokeDemonstrationTest.java（在 kairo-code-cli）

验证以下能力（不真实运行 LLM，只验证可以被调用）：
- `CodeAgentFactory.createSession` 正常工作
- `BashTool` 可以执行 mvn 命令格式
- 测试框架可以发现并运行自我修改类任务

### 3. 在 README 中记录 M5 完成状态

在 kairo-code 项目根目录创建或更新 `M5-STATUS.md`

## 验收标准

- [ ] 脚本 scripts/m5-self-mod-smoke.sh 创建
- [ ] M5SmokeDemonstrationTest 3+ 测试通过
- [ ] M5-STATUS.md 记录完成情况

## Agent 可以自主完成

YES
