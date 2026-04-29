状态: DONE
模块: kairo-tools
标题: BatchWriteTool — 批量写入多个文件

目标:
实现 BatchWriteTool，让 Agent 能一次调用写入多个文件，减少工具调用次数。

背景:
WriteTool 每次只能写一个文件。代码生成场景 Agent 需要多次调用。
BatchWriteTool 类似 WriteTool 的批量版本。

## 需要实现

### BatchWriteTool
`kairo-tools/src/main/java/io/kairo/tools/file/BatchWriteTool.java`

参数:
- `files`（required）: List，每项包含 `path`（String）和 `content`（String）
- `createDirs`（optional，默认 true）：自动创建父目录

行为：
- 最多 20 个文件，超出返回 error
- 单个文件失败不影响其他文件
- 返回每个文件的写入结果（path, success, error）
- metadata: successCount, errorCount
- workspace 模式下路径相对 workspace 根解析
- 路径越界（../ 逃逸）→ 该文件报错

### 测试（BatchWriteToolTest）
`kairo-tools/src/test/java/io/kairo/tools/file/BatchWriteToolTest.java`

场景（共 10+ 个）：
- 写入单个文件成功
- 批量写入 3 个文件
- createDirs=true 自动创建父目录
- 超过 20 个文件 → error
- 单个 path 为空 → 该文件报错，其他成功
- content 为空 → 写入空文件（允许）
- 路径越界 → 该文件报错
- metadata.successCount 和 errorCount 正确
- workspace 模式下路径相对根
- 缺少 files 参数 → error

约束:
- 不修改 kairo-api/
- 不新增外部依赖
