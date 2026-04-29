状态: DONE
模块: kairo-core
标题: DetectedToolCall 单元测试

目标:
先读取 DetectedToolCall 类，再补充单元测试。

测试场景（按实际 API 确定）:
- 构造后字段访问
- toString / equals / hashCode（如果是 record）
- null 参数处理
- 边界字段值

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/DetectedToolCallTest.java

约束:
- 不修改 kairo-api/
- 先读取源码确认 API 再写测试
