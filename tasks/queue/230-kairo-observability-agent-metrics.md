状态: DONE
模块: kairo-observability, kairo-spring-boot-starter-core
标题: AgentMetrics — Micrometer 指标集成

目标:
通过 Micrometer 暴露 Kairo Agent 运行时指标，供 Prometheus/Grafana 消费。

## 需要实现

`io.kairo.observability.AgentMetrics`
- 注册以下指标（通过 MeterRegistry）：
  - Gauge `kairo.agents.active`：AgentHealthRegistry.global().snapshot().size()
  - Gauge `kairo.agents.running`：snapshot 中 state==RUNNING 的数量
  - Gauge `kairo.agents.idle`：snapshot 中 state==IDLE 的数量
  - Counter `kairo.agent.calls.total`（由 DefaultReActAgent 在每次 call() 开始时 +1）
  - Timer `kairo.agent.call.duration`（记录每次 call() 的响应时间）
- 构造时接受 MeterRegistry

`io.kairo.spring.AgentMetricsAutoConfiguration`
- @AutoConfiguration(after = AgentActuatorAutoConfiguration.class)
- @ConditionalOnClass(io.micrometer.core.instrument.MeterRegistry.class)
- @ConditionalOnBean(Agent.class)
- 注册 AgentMetrics bean
- 添加到 AutoConfiguration.imports

修改 `DefaultReActAgent`：
- call() 开始时 counter.increment()
- call() 完成时 timer.record(duration)
- 通过 AgentMetrics 静态方法或注入（用 @Nullable 注入避免强依赖）

### 约束
- 不修改 kairo-api/
- Micrometer 依赖可选（ConditionalOnClass 保护）
- kairo-observability pom.xml 需添加 micrometer-core（optional=true）
