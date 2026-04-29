状态: DONE
模块: kairo-tools
标题: DiffTool — 生成 unified diff（内置 Myers diff）

目标:
实现 DiffTool，让 Agent 能对比文件变更，输出标准 unified diff 格式。

背景:
kairo-code 修改文件后需要验证变更内容。目前只能通过 BashTool 调用 `diff`，
不够结构化。DiffTool 提供内置 Myers diff 实现，无需外部库。

注意：此功能已在 PR #185 实现但尚未合并到 main。
如果该 PR 已合并则直接标记为 DONE；否则重新实现。

## 需要实现

### DiffTool
`kairo-tools/src/main/java/io/kairo/tools/file/DiffTool.java`

参数:
- `originalPath`（required）：原始文件路径（或 `-` 使用 originalContent）
- `modifiedPath`（required）：修改后文件路径（或 `-` 使用 modifiedContent）
- `originalContent`（optional）：inline 原始内容
- `modifiedContent`（optional）：inline 修改内容
- `contextLines`（optional，默认 3）：diff 上下文行数

输出：标准 unified diff 格式，含 ---/+++ 头和 @@ hunk 头

### 测试（DiffToolTest）
`kairo-tools/src/test/java/io/kairo/tools/file/DiffToolTest.java`

场景（共 10+ 个）：
- 相同内容 → "No differences found"
- 单行增加 → +line 输出
- 单行删除 → -line 输出
- 行替换 → -old +new 输出
- contextLines=0 只显示变更行
- 多处变更分为多个 hunk
- --- original / +++ modified 头正确
- 空→有内容（all inserts）
- 有内容→空（all deletes）
- 文件不存在 → error
- 基于文件路径的 diff

约束:
- 不修改 kairo-api/
- 不新增外部依赖
- 内联 Myers diff 算法
