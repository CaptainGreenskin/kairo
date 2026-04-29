状态: DONE
模块: kairo-core
标题: RateLimitMiddleware — 实现请求速率限制中间件

目标:
实现 RateLimitMiddleware，使用令牌桶算法控制每秒/每分钟的 Agent 调用频率，
防止触碰模型 API 的速率限制（429 错误）。

实现要求:
- 类路径: kairo-core/src/main/java/io/kairo/core/middleware/RateLimitMiddleware.java
- 实现 Middleware SPI
- 令牌桶: 可配置 requestsPerSecond / burstCapacity
- 超过限制时: 等待（blocking with timeout）或抛出 RateLimitExceededException
- 线程安全

测试文件:
- kairo-core/src/test/java/io/kairo/core/middleware/RateLimitMiddlewareTest.java

测试场景:
- 速率内的请求直接通过
- 超过速率时等待或抛异常
- 突发容量允许短期高并发
- 线程安全验证

约束:
- 不修改 kairo-api/
- 不引入新的外部依赖
