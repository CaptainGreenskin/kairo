状态: TODO
模块: kairo-observability
标题: AgentMetrics — Micrometer Agent 指标采集

目标:
基于 AgentCallObserver 和 AgentHealthRegistry 为 Micrometer 采集 Agent 级别指标，
让 Prometheus/Grafana 能监控 Agent 的活跃数和调用耗时。

## 需要实现（依赖任务 241 完成后执行）

`io.kairo.observability.AgentMetrics implements AgentCallObserver`：
- Gauge：`kairo.agents.active`（registry snapshot 大小）
- Gauge：`kairo.agents.running`（state=RUNNING 数量）
- Gauge：`kairo.agents.idle`（state=IDLE 数量）
- Counter：`kairo.agent.calls.total`
- Timer：`kairo.agent.call.duration`

`io.kairo.spring.AgentMetricsAutoConfiguration`（新增）：
- `@AutoConfiguration(after = AgentActuatorAutoConfiguration.class)`
- `@ConditionalOnClass(MeterRegistry.class)`
- Bean：`AgentMetrics`，注册后调用 `AgentCallObserver.setGlobal(metrics)`

注册进 `AutoConfiguration.imports`。

### 约束
- kairo-core 不依赖 Micrometer
- kairo-observability 已有 Micrometer 依赖（验证后使用）
- @ConditionalOnBean(MeterRegistry.class)
- 不修改 kairo-api/
