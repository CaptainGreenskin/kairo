状态: DONE
创建时间: 2026-04-26
优先级: P1（M3 核心：测试 100+）

## 目标

将 kairo-code 测试总数从当前 88 个提升至 100+ 个，覆盖未测试的边界场景。

## 背景

当前测试分布（main 分支，88 tests）：
- KairoCodeMainTaskFileTest: 4 tests（--task-file 基础场景）
- TaskExecutionLoggerTest: 6 tests
- RetryPolicyTest: 7 tests
- ErrorClassifierTest: 7 tests
- SkillCommandTest: 11 tests
- CommandRegistryTest: 12 tests
- ErrorRendererTest: 15 tests
- SnapshotResumeCommandTest: 13 tests（估算）
- TaskToolTest: 估算若干

## 需要新增的测试

### 1. KairoCodeMainOptionTest.java（新建）

测试场景（建议 8+ 个）：
- `--max-retries 0` 是默认值
- `--max-retries 5` 是上限
- `--max-retries 6` 应被拒绝（IllegalArgumentException）
- `--task-file` 和 `--task` 互斥时返回错误码 1
- `--task-file` 指向不存在的文件时返回错误码 1
- `--task-file` 指向空文件时不执行任务（直接 REPL 或错误）
- 缺少 `--api-key` 且无环境变量时返回错误码 1
- `--max-retries` 和 `--task-file` 可以同时使用

### 2. TaskExecutionLoggerCrResultTest.java（新建）

测试场景（建议 4+ 个）：
- crResult=PASS 时日志包含 `- CR结果：PASS`
- crResult=WARN 时日志包含 `- CR结果：WARN`
- crResult=FAIL 时日志包含 `- CR结果：FAIL`
- crResult=null（未设置）时日志不包含 CR 行

同时在 `TaskExecutionLogger.java` 的 `LogEntry` 中添加 `crResult` 字段。

## 验收标准

- [ ] 新增测试数量 ≥ 12（总计 ≥ 100 tests）
- [ ] `mvn test -pl kairo-code-cli` 全部通过
- [ ] TaskExecutionLogger.LogEntry 有 crResult 字段

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES
