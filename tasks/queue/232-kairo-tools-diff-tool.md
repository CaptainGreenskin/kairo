状态: DONE
模块: kairo-tools
标题: DiffTool — 纯 Java unified diff 生成器

目标:
让 Agent 能比较两段文本或文件，生成 unified diff 输出。
代码 review、补丁生成、变更记录等场景必备。

## 需要实现

`io.kairo.tools.file.DiffTool`
- @Tool(name="diff", sideEffect=READ_ONLY)
- 参数:
  - a（required）: 原始文本，或以 / 或 . 开头的文件路径
  - b（required）: 修改后文本，或文件路径
  - aLabel（optional, 默认 "a"）: diff 头部 --- 行的标签
  - bLabel（optional, 默认 "b"）: diff 头部 +++ 行的标签
  - contextLines（optional, 默认 3）: 每个 hunk 保留的上下文行数
- 输出标准 unified diff 格式（@@ -l,s +l,s @@ 头）
- 纯 Java 实现 Myers diff 算法（O(ND) 基础版即可）

### 约束
- 不修改 kairo-api/
- 不引入外部 diff 库
- 空 diff（无变化）返回空字符串，isError=false
