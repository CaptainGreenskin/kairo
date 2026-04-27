状态: IN_PROGRESS
模块: kairo-spring-boot-starter-core
标题: KairoAgentsEndpoint — Spring Actuator 代理状态端点

目标:
通过 Spring Actuator 暴露 /actuator/kairo-agents 端点，
让运维团队能实时查看所有运行中的 Agent 状态。

## 需要实现

`io.kairo.spring.KairoAgentsEndpoint`
- @Endpoint(id = "kairo-agents")
- @ReadOperation → 返回 AgentHealthRegistry.global().snapshot()
- JSON 输出：agentId, name, state, iterationCount, lastActivityAt

`io.kairo.spring.AgentActuatorAutoConfiguration`（修改）：
- 添加 @Bean @ConditionalOnMissingBean KairoAgentsEndpoint
- 检查是否已有此 bean 定义（避免与 task-224 PR 冲突）

### 约束
- 不修改 kairo-api/
- @ConditionalOnClass(AgentHealthRegistry.class) 保护
- AgentActuatorAutoConfiguration 需先 import kairo-core 的 AgentHealthRegistry
- kairo-core 必须已在 classpath（starter 已依赖）
