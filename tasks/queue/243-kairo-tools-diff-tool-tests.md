状态: DONE
模块: kairo-tools
标题: DiffTool 单元测试

目标:
为 DiffTool 补充单元测试，验证 unified diff 生成和文件解析。

## 需要实现

`io.kairo.tools.file.DiffToolTest`（10+ 个测试用例）

场景：
- 相同文本返回空 diff（identical=true, hunks=0）
- 单行替换生成 @@ 头和 -/+ 行
- 新增行（bText 多出几行）
- 删除行（aText 多出几行）
- contextLines 控制上下文行数
- 从文件读取（路径以 ./ 开头）
- 原始字符串输入（非文件路径）
- aLabel/bLabel 自定义标签出现在 ---/+++ 行
- 末尾换行处理正确
- 多个不连续修改生成多个 @@ 块

### 约束
- 使用 @TempDir 测试文件模式
- 不修改 kairo-api/
