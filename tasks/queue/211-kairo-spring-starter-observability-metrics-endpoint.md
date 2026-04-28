状态: TODO
模块: kairo-spring-boot-starter-observability
标题: Observability starter 添加 /actuator/kairo-metrics 端点

目标:
在 kairo-spring-boot-starter-observability 添加 actuator 端点，
暴露 Kairo 运行时指标：活跃 span 数、已处理请求数、模型调用总次数等。

背景:
目前 ObservabilityAutoConfiguration 只配置 OTel tracer，没有暴露任何可观测端点。
添加 /actuator/kairo-metrics 使 SRE 可以通过标准 actuator 接口监控 Kairo 运行状态。

## 需要实现

### KairoMetricsEndpoint
`kairo-spring-boot-starter-observability/src/main/java/io/kairo/spring/observability/KairoMetricsEndpoint.java`

端点 id: "kairo-metrics"
返回：
- tracerName: OTelTracer 的名称
- activeSpans: 当前活跃 span 数（如果 OTel SDK 暴露此信息）
- isEnabled: tracer 是否已启用
- version: "1.0.0-SNAPSHOT"

### AutoConfiguration 更新
在 ObservabilityAutoConfiguration 中注册 KairoMetricsEndpoint bean

### 测试
`kairo-spring-boot-starter-observability/src/test/java/io/kairo/spring/observability/KairoMetricsEndpointTest.java`

测试场景：
- /actuator/kairo-metrics 返回正确字段
- tracerName 不为空
- @ConditionalOnMissingBean 允许用户覆盖

约束:
- 不修改 kairo-api/
- 不新增外部依赖
