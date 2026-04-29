状态: IN_PROGRESS
模块: kairo-tools
标题: DiffTool — 纯 Java unified diff 生成工具

目标:
让 Agent 能比较两段文本或两个文件的差异，输出标准 unified diff 格式，
便于代码审查和变更验证。

## 需要实现

`io.kairo.tools.file.DiffTool`
- @Tool(name="diff", sideEffect=READ_ONLY)
- 参数:
  - a（required）: 原始文本或文件路径（以 ./ 或 / 开头时为文件）
  - b（required）: 修改后文本或文件路径
  - aLabel（optional, 默认 "a"）: diff 头部标签
  - bLabel（optional, 默认 "b"）: diff 头部标签
  - contextLines（optional, 默认 3）: 上下文行数
- 输出格式：标准 unified diff（--- a, +++ b, @@ hunk @@, -/+/ 行）
- 纯 Java LCS 算法（DP 表），无外部依赖
- 元数据：hunks（@@块数）, identical（是否相同）

### 约束
- 不修改 kairo-api/
- 不引入外部 diff 库
