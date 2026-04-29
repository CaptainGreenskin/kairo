状态: DONE
模块: kairo-core
标题: SystemPromptResult 单元测试

目标:
先读取 SystemPromptResult 类（kairo-core/.../prompt/SystemPromptResult.java），
再补充单元测试。

测试场景（按实际 API 确定）:
- 字段访问
- equals/hashCode（如果是 record）
- null 参数处理
- toString 含关键字段

新增文件:
- kairo-core/src/test/java/io/kairo/core/prompt/SystemPromptResultTest.java

约束:
- 不修改 kairo-api/
- 先读取源码确认 API
