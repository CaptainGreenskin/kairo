状态: DONE
创建时间: 2026-04-26
优先级: P0（早上 review 的基础）

## 目标

添加任务执行日志功能：当设置 `KAIRO_CODE_LOG_DIR` 环境变量时，
每次 `--task` 执行后将结果写入日志文件，支持早上批量 review。

## 上下文

- 相关模块：kairo-code-cli, kairo-code-core
- 相关文件：
  - `kairo-code-cli/src/main/java/io/kairo/code/cli/KairoCodeMain.java`
  - `kairo-code-cli/src/main/java/io/kairo/code/cli/StreamingAgentRunner.java`
- 夜间运行 5 个任务后，需要知道每个任务成功/失败及原因

## 需要实现

日志文件命名：`{KAIRO_CODE_LOG_DIR}/{yyyy-MM-dd}/{HH-mm-ss}-task.md`

日志文件内容格式：
```markdown
# Task Execution Log

- 开始时间：2026-04-26T02:30:00
- 结束时间：2026-04-26T02:47:23
- 状态：SUCCESS / FAILED / TIMEOUT
- 任务来源：--task-file tasks/queue/001-xxx.md

## 任务描述
（任务文件内容或 --task 字符串）

## 执行摘要
（Agent 最后一条输出，截取前 500 字）

## 工具调用统计
- 总调用次数：12
- 成功：11
- 失败：1

## 错误信息（如有）
（异常或失败原因）
```

## 验收标准

- [ ] `KAIRO_CODE_LOG_DIR=./logs java -jar ... --task "xxx"` 生成日志文件
- [ ] 未设置环境变量时不生成日志（静默）
- [ ] 日志目录不存在时自动创建
- [ ] 单元测试覆盖日志写入和目录创建
- [ ] `mvn test -pl kairo-code-cli` 通过

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES

---
## 完成记录
- 时间：2026-04-26
- 分支：feature/task-002-task-execution-log
- 改动：新增 TaskExecutionLogger.java + TaskExecutionLoggerTest.java（6个测试）
- 测试：70/70 通过
