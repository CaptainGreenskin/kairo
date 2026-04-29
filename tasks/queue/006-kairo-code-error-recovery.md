状态: DONE
创建时间: 2026-04-26
优先级: P1（M3 核心：错误恢复）

## 目标

为 kairo-code CLI 添加任务执行失败时的重试机制和错误恢复。

## 背景

当前 kairo-code 遇到任务失败时直接退出，没有重试。
M3 目标是让 kairo-code 更健壮，能够自主从失败中恢复。

## 需要实现

**文件：`kairo-code-cli/src/main/java/io/kairo/code/cli/`**

1. 新增 `RetryPolicy.java`：
   - 最大重试次数（默认 2）
   - 指数退避延迟（1s → 2s → 4s）
   - 可配置的 CLI 选项 `--max-retries <n>`（0-5）

2. 修改 `KairoCodeMain.java`：
   - 集成 RetryPolicy
   - 每次重试前记录日志（"重试第 N 次..."）
   - 全部重试失败后，`TaskExecutionLogger` 记录 FAILED

3. 新增 `ErrorClassifier.java`：
   - 区分"可重试错误"（网络超时、速率限制）和"不可重试错误"（任务文件不存在、语法错误）
   - 不可重试错误立即失败，不消耗重试次数

## 验收标准

- [ ] `--max-retries 2` 时，失败任务最多执行 3 次（1 次 + 2 次重试）
- [ ] `--max-retries 0` 时，不重试
- [ ] 不可重试错误（文件不存在）不触发重试
- [ ] 新增测试：RetryPolicyTest.java（至少 5 个测试用例）
- [ ] 全部测试通过（`mvn test -pl kairo-code-cli`）

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES
