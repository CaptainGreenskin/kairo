状态: DONE
模块: kairo-tools
标题: BatchWriteTool — 原子多文件写入工具

目标:
让 Agent 在一次工具调用中写入多个文件，提升代码生成效率，
避免因多次 WriteTool 调用之间的中断导致项目处于不一致状态。

## 需要实现

`io.kairo.tools.file.BatchWriteTool`
- @Tool(name="batch_write", sideEffect=WRITE)
- 参数:
  - files（required）: JSON 数组，每个元素包含：
    - path（required）: 文件路径（相对 workspace root 或绝对路径）
    - content（required）: 写入内容
    - createDirs（optional, 默认 true）: 是否自动创建父目录
  - dryRun（optional, 默认 false）: 只验证路径合法性，不实际写入
- 两阶段执行：
  Phase 1: 验证所有路径合法（在 workspace 内、无路径遍历）
  Phase 2: 全部写入（任一失败则尝试回滚已写入的文件）
- 返回每个文件的写入状态

### 约束
- 不修改 kairo-api/
- 路径必须在 workspace root 内（防止路径遍历攻击）
- 单次调用最多 50 个文件
