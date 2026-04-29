状态: DONE
模块: kairo-observability
标题: KairoObservability 常量 + 观测相关类测试

目标:
先读取观测模块所有类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- KairoObservability: MODULE_NAME/MODULE_VERSION 常量值
- 其他观测类（GenAiAttributes 等）：字段/常量存在性测试

新增文件:
- kairo-observability/src/test/java/io/kairo/observability/KairoObservabilityTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
