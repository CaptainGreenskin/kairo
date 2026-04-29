状态: DONE
模块: kairo-core
标题: AnthropicErrorClassifier + ApiErrorClassifierImpl 单元测试

目标:
先读取两个 error classifier 类源码，补充测试。

测试场景（按实际 API 确定）:
- classify() 对常见错误码/消息返回正确分类
- 速率限制错误 → RATE_LIMITED
- 认证错误 → AUTHENTICATION
- 未知错误 → UNKNOWN 或 OTHER
- null 处理

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/AnthropicErrorClassifierTest.java
- kairo-core/src/test/java/io/kairo/core/model/ApiErrorClassifierImplTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认 classify() 方法签名
