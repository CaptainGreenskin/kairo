状态: DONE
模块: kairo-tools
标题: BatchWriteTool — 批量写入多个文件

目标:
实现 BatchWriteTool，让 Agent 能一次调用写入多个文件，减少工具调用次数，
提升代码生成效率。

背景:
当前 WriteTool 每次只能写一个文件，在代码生成场景中 Agent 需要多次调用。
BatchWriteTool 类似 BatchReadTool 的写入版本。

## 需要实现

### BatchWriteTool
`kairo-tools/src/main/java/io/kairo/tools/file/BatchWriteTool.java`

参数:
- `files`（required）：List<Map>，每个 Map 包含 `path`（String）和 `content`（String）
- `createDirs`（optional，默认 true）：是否自动创建父目录

输出：
- 每个文件的写入结果（成功/失败）
- metadata: successCount, errorCount

行为：
- 最多 20 个文件
- 单个文件失败不影响其他文件
- 返回详细结果列表（path, success, error message）

### 测试
`kairo-tools/src/test/java/io/kairo/tools/file/BatchWriteToolTest.java`

测试场景（共 10+ 个）：
- 写入单个文件成功
- 批量写入多个文件
- 自动创建父目录
- 超过 20 个文件 → error
- 单个文件 path 为空 → 该文件报错，其他成功
- content 为空 → 写入空文件（允许）
- 路径越界（../ 逃逸）→ error
- metadata.successCount 正确
- workspace 模式下路径相对 workspace 根

约束:
- 不修改 kairo-api/
- 不新增外部依赖
