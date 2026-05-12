状态: DONE
优先级: P2
模块: kairo-tools
标题: TemplateRenderTool 单元测试

目标:
为 TemplateRenderTool 编写单元测试。TemplateRenderTool 实现纯 Java Mustache 子集渲染，
支持 {{var}}（HTML 转义）、{{{var}}}（原始）、{{#sec}}...{{/sec}}（section）、
{{^sec}}...{{/sec}}（inverted section）、{{! comment}}。

## 需要实现

`io.kairo.tools.file.TemplateRenderToolTest`（10+ 测试用例）

场景（使用 @TempDir 创建模板文件）：
- {{var}} 变量替换基础场景
- {{{var}}} 不进行 HTML 转义（保留 `<`, `>`）
- {{var}} 对 `<`, `>`, `&` 进行 HTML 转义
- {{#sec}}...{{/sec}} truthy section：变量值为 true/非空时渲染 body
- {{#sec}}...{{/sec}} array section：变量为数组时迭代渲染
- {{^sec}}...{{/sec}} inverted section：变量为 false/null 时渲染 body
- {{! comment }} 注释内容不出现在输出中
- template 文件不存在 → isError=true
- variables 参数为 null/缺失时模板中未引用变量保持原样或为空
- 输出写入 outputPath（如果指定），且 metadata 含 outputPath
- 渲染结果直接返回在 output 字段（不写文件时）

### 约束
- 不引入 Mustache 第三方库（工具本身是纯 Java 实现）
- 使用 @TempDir 管理临时模板文件
- 不修改 kairo-api/
