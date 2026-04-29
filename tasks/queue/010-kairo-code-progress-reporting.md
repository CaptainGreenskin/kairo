状态: DONE
创建时间: 2026-04-26
优先级: P2（M4 预备：进度上报）

## 目标

为 kairo-code 单次任务模式（--task/--task-file）添加结构化进度输出，
让用户和自动化系统能够感知任务执行进度。

## 背景

当前单次任务模式输出：
- 无进度（只有最终结果）
- 用户看不到 Agent 在做什么
- 自动化无法判断任务是否卡住

M4 目标"进度上报"的前置步骤。

## 需要实现

### 1. `AgentEventPrinter.java` 扩展

在现有 AgentEventPrinter 基础上：
- 打印 `[STEP n] 工具调用：<tool-name>` 到 stderr（不污染 stdout 结果）
- 打印 `[THINKING] <前100字符>` 到 stderr（可用 --verbose 开启）

### 2. `KairoCodeMain.java` 添加 `--verbose`

```java
@Option(names = "--verbose", description = "Show step-by-step progress on stderr")
private boolean verbose;
```

在 runOneShot 中：根据 `--verbose` 决定是否传入 AgentEventPrinter。

### 3. 测试：`AgentProgressTest.java`（至少 4 个用例）

- verbose=false 时 stderr 无 STEP 输出
- verbose=true 时 stderr 包含工具调用事件
- STEP 计数从 1 开始递增
- 输出格式正确

## 验收标准

- [ ] `--verbose` 时 stderr 输出 `[STEP n] 工具调用：xxx`
- [ ] 不影响 stdout（最终响应仍走 stdout）
- [ ] 新增 4+ 测试，全部通过

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES
