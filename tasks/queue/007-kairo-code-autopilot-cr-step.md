状态: DONE
创建时间: 2026-04-26
优先级: P1（M3 核心：自动 CR 环节）

## 目标

在 kairo-code 自动化流程中，commit 之前插入 **自我 Code Review（CR）** 环节：
Agent 用 Claude 对自己的改动做一轮代码审查，发现问题先修复再提交。

## 背景

当前自动化流程：实现 → 测试通过 → commit
目标流程：实现 → 测试通过 → **CR 自我审查** → （必要时修复）→ commit

CR 能捕获测试覆盖不到的问题：SPI 越界、阻塞调用混入响应式链、安全漏洞、命名不一致等。

## 需要实现

### 1. `tasks/cr.sh` — CR 脚本（Shell）

```bash
#!/usr/bin/env bash
# 对 git diff --staged 的内容调用 kairo-code 做 review
# 输出：PASS / WARN / FAIL + 问题列表

DIFF=$(git diff --staged)
if [[ -z "$DIFF" ]]; then
    echo "CR: 没有暂存的改动"
    exit 0
fi

# 构造 review prompt
PROMPT="你是 Kairo 框架的 code reviewer。请 review 以下 diff，检查：
1. 是否修改了 kairo-api/ SPI（违规则输出 FAIL）
2. 是否在 Reactive 链中引入阻塞调用（block/get/join）
3. 是否有明显安全漏洞（命令注入、路径穿越）
4. 测试覆盖是否明显不足
5. 命名和代码风格是否符合 AOSP Java

输出格式：
CR_RESULT: PASS|WARN|FAIL
ISSUES:
- [CRITICAL] xxx
- [WARNING] xxx"

echo "$DIFF" | kairo-code --task "$PROMPT" --task-file /dev/stdin
```

### 2. 更新 `tasks/AUTOPILOT.md`

在 Step 2A 第 5 步（测试通过后）、第 6 步（commit）之间插入：

```
5.5. 运行 `bash tasks/cr.sh`
     - CR_RESULT=PASS：继续 commit
     - CR_RESULT=WARN：记录警告，继续 commit
     - CR_RESULT=FAIL：修复问题后重新测试，修复失败则状态改 BLOCKED
```

### 3. `TaskExecutionLogger.java` 添加 crResult 字段

在日志文件中记录 CR 结果（PASS/WARN/FAIL/SKIPPED）。

## 验收标准

- [ ] `tasks/cr.sh` 有 execute 权限，能够运行
- [ ] AUTOPILOT.md 包含 CR 步骤说明
- [ ] TaskExecutionLogger 记录 crResult 字段
- [ ] 新增测试：TaskExecutionLoggerTest 覆盖 crResult 字段
- [ ] 在 macOS zsh 环境下可运行

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES
