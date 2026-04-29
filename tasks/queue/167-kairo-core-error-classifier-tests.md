状态: DONE
模块: kairo-core
标题: AnthropicErrorClassifier + OpenAIErrorClassifier + ApiErrorClassifierImpl 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- AnthropicErrorClassifier: 429 isRateLimit、5xx isServerError、4xx not isServerError
- OpenAIErrorClassifier: 429 isRateLimit、503 isServerError、200 not isError
- ApiErrorClassifierImpl: 委托给具体 classifier 的逻辑

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/AnthropicErrorClassifierTest.java
- kairo-core/src/test/java/io/kairo/core/model/openai/OpenAIErrorClassifierTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
