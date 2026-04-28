状态: IN_PROGRESS
模块: kairo-tools
标题: JsonQueryTool — jq 风格 JSON 查询工具（纯 Java）

目标:
实现 JsonQueryTool，让 Agent 能对 JSON 数据执行结构化查询，
无需依赖外部 jq 命令。

## 需要实现

`io.kairo.tools.file.JsonQueryTool`
- @Tool(name="json_query", sideEffect=READ_ONLY)
- 参数:
  - json（required）: JSON 字符串或文件路径（以 / 或 . 开头视为路径）
  - query（required）: 简化 jq 路径表达式
  - pretty（optional, 默认 true）: 是否格式化输出
- 支持的查询语法（优先实现以下子集）:
  - `.field` — 字段访问
  - `.field.nested` — 嵌套字段
  - `.array[0]` — 数组索引
  - `.array[]` — 展开数组（每个元素一行）
  - `.array[] | .field` — 管道过滤
  - `keys` — 返回对象所有 key
  - `length` — 数组/字符串长度
  - `type` — 返回类型名
- 实现方式：用 Jackson ObjectMapper 解析，递归执行 AST 解析的路径表达式
- 解析失败返回明确错误（不 throw）

### 约束
- 不修改 kairo-api/
- 使用 Jackson（已有依赖），不引入 jq 库
- 单文件实现（JsonQueryTool.java 内部类做 AST）
