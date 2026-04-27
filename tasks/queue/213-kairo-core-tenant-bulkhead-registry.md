状态: DONE
模块: kairo-core, kairo-starters
标题: D1 TenantBulkheadRegistry — 按租户隔离并发与速率

目标:
实现 per-tenant 隔离机制（bulkhead + rate limiter），防止单一租户打满资源池影响其他租户。
不引入 Resilience4j，用 Java 内置 Semaphore + 自研 TokenBucket 实现。

## 需要实现

### 核心类（kairo-core）

`io.kairo.core.tenant.TierBulkheadLimits`
- record: maxConcurrent(int), ratePerSecond(double)
- 静态工厂：FREE(5, 2.0), PRO(20, 10.0), ENTERPRISE(100, 50.0)

`io.kairo.core.tenant.TenantBulkheadConfig`
- 按 tier 或 tenantId 定制限制
- 方法：limitsFor(TenantContext) → TierBulkheadLimits

`io.kairo.core.tenant.TokenBucket`
- 纯 Java synchronized 实现：ratePerSecond, tokens, lastRefillNanos
- tryAcquire() 方法

`io.kairo.core.tenant.TenantBulkhead`
- 持有 Semaphore + TokenBucket
- execute(Supplier<T>) → T，先 acquire rate，再 acquire semaphore，完成后 release
- 拒绝时抛 BulkheadRejectedException（新建）

`io.kairo.core.tenant.TenantBulkheadRegistry`
- ConcurrentHashMap<String, TenantBulkhead>
- getOrCreate(TenantContext) → TenantBulkhead
- 支持自定义 TenantBulkheadConfig 注入

`io.kairo.core.middleware.BulkheadMiddleware`
- 实现 Middleware 接口
- 在 PreModelCall hook 点包装推理请求
- 从 AgentContext 提取 TenantContext，调用 registry.getOrCreate().execute()

### Spring Boot 自动配置（kairo-spring-boot-starter-core）

`KairoBulkheadAutoConfiguration`
- @ConditionalOnMissingBean(TenantBulkheadRegistry.class)
- 默认注册 BulkheadMiddleware

### 约束
- 不修改 kairo-api/
- 不引入外部依赖
- 并发测试：100 个租户并发请求，验证隔离效果
