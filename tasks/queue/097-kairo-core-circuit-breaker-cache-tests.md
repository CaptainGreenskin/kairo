状态: DONE
模块: kairo-core
标题: CircuitBreakerOpenException + CacheCheckResult 测试

目标:
先读取两个类，再补充测试。

CircuitBreakerOpenException (kairo-core/.../model/CircuitBreakerOpenException.java):
- 是 ModelUnavailableException 子类
- getMessage() 包含 modelId
- modelId 字段可访问

CacheCheckResult (kairo-core/.../model/anthropic/CacheCheckResult.java):
- 字段访问（cacheReadTokens, cacheCreationTokens, inputTokens）
- hitRatio(): read/(read+creation)，total=0 时返回 0.0
- hitRatio(): 全 cache hit = 1.0
- hitRatio(): 混合场景
- cacheBroken 标记及 reasons 列表（先读取完整源码确认 API）

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/CircuitBreakerOpenExceptionTest.java
- kairo-core/src/test/java/io/kairo/core/model/anthropic/CacheCheckResultTest.java

约束:
- 不修改 kairo-api/
- 先读取完整 CacheCheckResult 源码再写测试
