状态: DONE
模块: kairo-tools
标题: TemplateRenderTool — Mustache 模板渲染工具

目标:
让 Agent 能渲染 Mustache 模板（代码生成、文档生成场景），
不依赖 JTE/Freemarker 等重量级模板引擎。

## 需要实现

`io.kairo.tools.file.TemplateRenderTool`
- @Tool(name="template_render", sideEffect=WRITE)
- 参数:
  - template（required）: Mustache 模板字符串，或以 / 开头的文件路径
  - variables（required）: JSON 对象，注入到模板的变量
  - outputPath（optional）: 渲染结果写入的文件路径；不提供则只返回字符串
- Mustache 子集实现（纯 Java）：
  - `{{variable}}` — 变量替换（HTML 转义）
  - `{{{variable}}}` — 无转义替换
  - `{{#section}}...{{/section}}` — 条件/循环（truthy / array）
  - `{{^section}}...{{/section}}` — 反条件
  - `{{! comment }}` — 注释
- 使用 Jackson 解析 variables JSON
- 输出写入 outputPath（如指定），同时返回渲染结果

### 约束
- 不修改 kairo-api/
- 纯 Java 实现（不引入 mustache.java 等库）
- 模板解析错误返回 error ToolResult（不 throw）
