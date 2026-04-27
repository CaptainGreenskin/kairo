状态: DONE
模块: kairo-core, kairo-starters
标题: AgentHealthEndpoint — Agent 运行时健康检查

目标:
提供 /actuator/kairo/agents 端点，暴露当前运行中的 Agent 状态，
方便运维监控和 kairo-code 自检。

## 需要实现

`io.kairo.core.health.AgentHealthRegistry`
- 全局 Registry（单例），记录所有已创建 Agent 的状态
- register(Agent), deregister(agentId)
- snapshot(): List<AgentHealthInfo>

`io.kairo.core.health.AgentHealthInfo`
- record: agentId, name, state(AgentState), iterationCount(int), lastActivityAt(Instant)

`io.kairo.spring.starter.actuator.KairoAgentsEndpoint`
- @Endpoint(id = "kairo-agents")
- @ReadOperation: 返回所有 Agent 的 AgentHealthInfo 列表
- 集成进 spring-boot-actuator

修改 `DefaultReActAgent`（或其工厂）：
- 创建时 register，销毁/interrupt 时 deregister

### 约束
- 不修改 kairo-api/
- 端点只读（ReadOperation）
- 在 kairo-spring-starter-core 或新 kairo-spring-starter-actuator 中注册
