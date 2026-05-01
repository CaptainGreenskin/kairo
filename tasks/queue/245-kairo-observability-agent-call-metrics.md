状态: DONE
优先级: P2
模块: kairo-observability
标题: AgentCallMetrics — Micrometer 指标注册

目标:
在 kairo-observability 模块实现 AgentCallObserver，注册 Micrometer 指标，
利用 kairo-core 的 AgentCallObserver SPI（PR #222）。

## 需要实现

`io.kairo.observability.AgentCallMetrics`（实现 AgentCallObserver）：
- 构造函数接受 `MeterRegistry`
- `onCallStart`：记录 `kairo.agent.calls.active`（Gauge 或 Counter）
- `onCallEnd`：
  - `kairo.agent.calls.total`（Counter，tag: agentName, success=true/false）
  - `kairo.agent.call.duration`（Timer，tag: agentName, success=true/false）

Spring 自动配置：
- `KairoObservabilityAutoConfiguration` 中检测到 `AgentCallObserver` SPI 存在时，
  自动 `AgentCallObserver.setGlobal(new AgentCallMetrics(meterRegistry))`

### 约束
- 不修改 kairo-api/
- kairo-observability 已有 Micrometer 依赖（不新增）
- 使用 @ConditionalOnClass(MeterRegistry.class) 防止无 Micrometer 时出错
