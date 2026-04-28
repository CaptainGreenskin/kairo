状态: IN_PROGRESS
模块: kairo-core, kairo-spring-boot-starter-core
标题: AgentMetricsCollector — per-session 执行指标收集

目标:
实现 Agent 会话级别的执行指标收集，支持 Micrometer 计数器上报和 Spring Boot Actuator 暴露。

背景:
生产环境需要监控：Agent 每次调用用了多少 token、执行了几次迭代、调了哪些工具多少次。
目前 ProgressSnapshot（任务 201）跟踪当前会话，但指标需要跨会话聚合。

## 需要实现

### AgentSessionMetrics（kairo-core）
`kairo-core/src/main/java/io/kairo/core/agent/AgentSessionMetrics.java`
```java
public record AgentSessionMetrics(
    String agentId,
    String agentName,
    Instant startTime,
    Instant endTime,         // null if still running
    long totalTokensUsed,
    int totalIterations,
    int totalToolCalls,
    Map<String, Integer> toolCallCounts,  // tool name → count
    boolean succeeded,
    String failureReason     // null if succeeded
) {}
```

### AgentMetricsCollector（kairo-core）
`kairo-core/src/main/java/io/kairo/core/agent/AgentMetricsCollector.java`
- 收集每次 agent 执行的 AgentSessionMetrics
- 内存环形缓冲（保留最近 1000 条）
- `record(metrics)`, `getRecent(n)`, `getSummary()` API
- `AgentMetricsSummary`: total invocations, avg tokens, avg iterations, success rate

### MicrometerAgentMetrics（kairo-spring-boot-starter-core）
`kairo-spring-boot-starter-core/src/main/java/io/kairo/spring/MicrometerAgentMetrics.java`
- @Bean 条件创建（仅当 Micrometer 在 classpath 时）
- 注册 Counter: `kairo.agent.invocations.total{success=true/false}`
- 注册 DistributionSummary: `kairo.agent.tokens.used`, `kairo.agent.iterations`

### AgentMetricsEndpoint（kairo-spring-boot-starter-core）
- `/actuator/agent-metrics`：返回 AgentMetricsSummary + 最近 20 条会话

### 测试
- `AgentSessionMetricsTest`：record 构建和字段验证
- `AgentMetricsCollectorTest`：circular buffer 边界、getSummary 计算
- 共 12+ 测试

约束:
- 不修改 kairo-api/
- MicrometerAgentMetrics 用 @ConditionalOnClass(MeterRegistry.class)
