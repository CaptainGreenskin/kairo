状态: TODO
模块: kairo-tools
标题: BatchReadTool + SearchReplaceTool（编程 Agent 核心工具）

目标:
添加两个编程 Agent 高频使用的工具，减少 ReAct loop 迭代次数。

背景:
当前 Agent 每次只能读一个文件（ReadTool），需要多轮才能读多文件。
EditTool 基于字符串匹配，需要精确匹配旧内容；SearchReplaceTool 
提供正则替换，更适合结构化代码修改。

## 需要实现

### BatchReadTool
`kairo-tools/src/main/java/io/kairo/tools/file/BatchReadTool.java`

参数:
- `paths`（required）：JSON 数组，最多 20 个文件路径
- `maxLinesPerFile`（optional，默认 500）：每个文件最大读取行数

返回格式（content）:
```
=== /path/to/file1.java ===
<content>

=== /path/to/file2.java ===
<content>
```

错误处理：单个文件失败时标记为 `[ERROR: <reason>]`，不影响其他文件。

### SearchReplaceTool
`kairo-tools/src/main/java/io/kairo/tools/file/SearchReplaceTool.java`

参数:
- `path`（required）：文件路径
- `search`（required）：正则表达式（Java Pattern 语法）
- `replace`（required）：替换字符串（支持 $1、$2 组引用）
- `replaceAll`（optional，默认 true）：替换全部匹配还是仅第一个
- `flags`（optional，如 "CASE_INSENSITIVE"）

安全约束：
- 拒绝超过 1000 字符的正则（防止 ReDoS）
- 替换后文件大小不得超过 10MB

### 测试
- `BatchReadToolTest`：正常多文件读取、部分文件不存在、超过数量限制
- `SearchReplaceToolTest`：基本替换、组引用、CASE_INSENSITIVE、ReDoS 保护
- 共 15+ 测试

约束:
- 不修改 kairo-api/
- 不新增外部依赖（只用 JDK 内置）
