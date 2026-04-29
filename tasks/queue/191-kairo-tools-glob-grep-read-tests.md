状态: DONE
模块: kairo-tools
标题: 补充 GlobTool / GrepTool / ReadTool 边界测试

目标:
先读取源码，补充 GlobTool、GrepTool、ReadTool 的边界和错误场景测试。

测试场景（按实际 API 确定）:
GlobTool:
- 无匹配返回空列表
- 递归模式匹配多层目录
- 无效 pattern 不崩溃

GrepTool:
- 正则匹配多行结果
- 无匹配返回空
- 无效正则返回错误

ReadTool:
- 读取不存在的文件返回错误
- 读取二进制文件不崩溃
- 超大文件截断处理

涉及文件:
- kairo-tools/src/test/java/io/kairo/tools/file/GlobToolTest.java（已有，追加）
- kairo-tools/src/test/java/io/kairo/tools/file/GrepToolTest.java（已有，追加）
- kairo-tools/src/test/java/io/kairo/tools/file/ReadToolTest.java（已有，追加）

约束:
- 不修改 kairo-api/
- 先读完整源码
