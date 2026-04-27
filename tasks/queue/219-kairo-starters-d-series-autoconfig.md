状态: IN_PROGRESS
模块: kairo-starters
标题: Spring Boot 自动配置 D 系列组件（Bulkhead + Outbox + DockerSandbox）

目标:
为 D1/D3/D4 组件提供 Spring Boot starter 自动配置，开箱即用。

## 需要实现

`kairo-spring-starter-core` 或新 `kairo-spring-starter-distributed`：

`KairoBulkheadAutoConfiguration`
- 暴露 TenantBulkheadRegistry bean（条件：@ConditionalOnMissingBean）
- 从 kairo.bulkhead.default-tier 属性读取默认限制
- 自动注册 BulkheadMiddleware 到 MiddlewarePipeline

`KairoOutboxAutoConfiguration`
- 暴露 InMemoryOutboxStore、TransactionalOutboxPublisher、OutboxPoller beans
- OutboxPoller 在 SmartLifecycle.start() 启动，stop() 时关闭
- 条件：@ConditionalOnBean(KairoEventBus.class)

`KairoDockerSandboxAutoConfiguration`
- 从 kairo.sandbox.docker.image 属性读取镜像
- 条件：@ConditionalOnProperty("kairo.sandbox.docker.enabled", havingValue="true")
- 暴露 DockerSandbox bean（替换 LocalProcessSandbox）

### 约束
- 不修改 kairo-api/
- 保持向后兼容（ConditionalOnMissingBean）
- 在 spring.factories / AutoConfiguration.imports 中注册
