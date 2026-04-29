状态: DONE
模块: kairo-core
标题: ModelTier + OpenAIErrorClassifier 单元测试

目标:
先读取两个类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- ModelTier: enum 值存在，fromModelId() 正确分类模型名称
- OpenAIErrorClassifier: isRetryable/isRetryableError 对常见错误类型

新增文件:
- kairo-core/src/test/java/io/kairo/core/routing/ModelTierTest.java
- kairo-core/src/test/java/io/kairo/core/model/openai/OpenAIErrorClassifierTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
