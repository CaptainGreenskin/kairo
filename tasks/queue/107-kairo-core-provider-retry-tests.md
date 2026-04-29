状态: DONE
模块: kairo-core
标题: ProviderRetry 常量和 isTransientProviderError 测试

目标:
先读取 ProviderRetry 完整源码，再对静态方法和常量补充测试。

测试场景:
- DEFAULT_MAX_ATTEMPTS = 3
- DEFAULT_MIN_BACKOFF = 1 秒
- DEFAULT_MAX_BACKOFF = 4 秒
- DEFAULT_JITTER 在 0.0-1.0 之间
- isTransientProviderError(null) = false
- isTransientProviderError(TimeoutException) = true
- isTransientProviderError(ModelRateLimitException) = true
- isTransientProviderError(普通 RuntimeException) = false
- isTransientProviderError(ModelApiException "HTTP 500 ...") = true
- isTransientProviderError(ModelApiException "HTTP 503 ...") = true
- isTransientProviderError(ModelApiException null message) = false
- 构造函数是 private（反射验证）

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/ProviderRetryTest.java

约束:
- 不修改 kairo-api/
- 先读取完整源码确认常量和方法签名
