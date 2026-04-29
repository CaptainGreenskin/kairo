状态: DONE
模块: kairo-tools
标题: DiffTool — 生成文件 unified diff（代码 review 必备）

目标:
实现 DiffTool，让 Agent 能对比文件变更，生成标准 unified diff 格式输出。

背景:
kairo-code 在修改文件后需要验证变更内容。目前只能通过 BashTool 调用 `diff` 命令，
但不够结构化。DiffTool 提供内置实现：JDK 无内置 diff 算法，使用 Myers diff。

## 需要实现

### DiffTool
`kairo-tools/src/main/java/io/kairo/tools/file/DiffTool.java`

参数:
- `originalPath`（required）：原始文件路径（或 `-` 表示使用 originalContent）
- `modifiedPath`（required）：修改后文件路径（或 `-` 表示使用 modifiedContent）
- `originalContent`（optional）：当 originalPath 为 `-` 时使用
- `modifiedContent`（optional）：当 modifiedPath 为 `-` 时使用
- `contextLines`（optional，默认 3）：diff 上下文行数

输出：标准 unified diff 格式，包含 `---`/`+++` 头和 `@@` hunk 头

### Myers Diff 实现
- 内联 Myers diff 算法（不引入外部库）
- 输出 unified diff 格式

### 测试（DiffToolTest）
- 相同文件 → 空 diff（输出 "No differences found"）
- 单行增加 → 正确 `+line` 输出
- 单行删除 → 正确 `-line` 输出
- 多行修改 → 正确 hunk 输出
- 使用 originalContent/modifiedContent 参数
- 文件不存在 → error
- contextLines=0 → 只显示变更行
- 共 10+ 测试

约束:
- 不修改 kairo-api/
- 不新增外部依赖
