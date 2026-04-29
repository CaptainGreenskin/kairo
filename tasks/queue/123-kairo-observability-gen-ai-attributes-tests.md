状态: DONE
模块: kairo-observability
标题: GenAiSemanticAttributes 常量测试

目标:
为 GenAiSemanticAttributes 常量类补充测试。

测试场景:
- 读取源码确认常量定义方式
- 关键常量不为 null / 不为空字符串
- 常量名称遵循 gen_ai.* 命名规范

新增文件:
- kairo-observability/src/test/java/io/kairo/observability/GenAiSemanticAttributesTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
