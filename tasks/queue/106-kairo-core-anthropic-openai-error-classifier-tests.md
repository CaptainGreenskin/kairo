状态: DONE
模块: kairo-core
标题: AnthropicErrorClassifier + OpenAIErrorClassifier 单元测试

目标:
先读取两个分类器和 ProviderRetry.isTransientProviderError，再补充测试。

AnthropicErrorClassifier 测试场景:
- 实现 ProviderPipeline.ErrorClassifier 接口
- TimeoutException → isRetryable=true
- ModelRateLimitException → isRetryable=true
- 普通 RuntimeException → isRetryable=false
- null message 的 ApiException → isRetryable=false
- "HTTP 500" ApiException → isRetryable=true

OpenAIErrorClassifier 测试场景（与 Anthropic 对称）:
- 实现 ProviderPipeline.ErrorClassifier 接口
- TimeoutException → isRetryable=true
- 普通 RuntimeException → isRetryable=false

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/AnthropicErrorClassifierTest.java
- kairo-core/src/test/java/io/kairo/core/model/openai/OpenAIErrorClassifierTest.java

约束:
- 不修改 kairo-api/
- 先读取 ModelProviderException 确认内部异常类型
