状态: DONE
创建时间: 2026-04-26
优先级: P1（M4 核心：并行任务）

## 目标

为 kairo-code 添加并行任务执行能力：`--task-list <file>` 选项，读取一个包含多个任务的文件，并发运行所有任务，合并输出。

## 背景

M4 里程碑目标之一：并行任务。场景：同时对多个代码库跑 code review，或并发执行多个分析任务。

## 需要实现

### 1. 任务列表文件格式（JSON Lines）

```
{"id": "task-1", "task": "分析 foo.java 的复杂度"}
{"id": "task-2", "task": "检查 bar.java 是否有 SQL 注入"}
{"id": "task-3", "task": "给 baz.java 写单元测试"}
```

### 2. KairoCodeMain.java 添加选项

```java
@Option(names = "--task-list", description = "JSON Lines file with multiple tasks to run in parallel")
private Path taskListFile;
```

### 3. 实现 runParallel 方法

- 读取文件，每行解析为 {id, task}
- 每个任务创建独立 Agent（CodeAgentFactory.create）
- 用 Flux.merge 或 Mono.zip 并发执行
- 输出格式：`=== task-1 ===\n<response>\n`
- 失败的任务打印错误，不影响其他任务

### 4. 与现有选项兼容

- --task-list 与 --task / --task-file 互斥
- --timeout 同样适用（每个 task 独立超时）

### 5. 测试：ParallelTasksTest.java（至少 4 个用例）

- task-list 与 --task 互斥
- 不存在的文件返回退出码 1
- 空文件正常完成（没有任务要跑）
- 格式错误的行跳过并打印警告

## 验收标准

- [ ] `--task-list tasks.jsonl` 并发执行所有任务
- [ ] 单个任务失败不影响其他
- [ ] 与 --task 互斥
- [ ] 4+ 测试通过

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES
