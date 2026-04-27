状态: DONE
模块: kairo-spring-boot-starter-core
标题: KairoAgentsEndpoint 单元测试

目标:
为 KairoAgentsEndpoint 补充单元测试，验证 JSON 结构和 registry 集成。

## 需要实现

`io.kairo.spring.KairoAgentsEndpointTest`（8+ 测试用例）

场景：
- 空 registry → agents=[], count=0
- 一个活跃 agent → agents 包含 agentId/name/state/iterationCount/lastActivityAt
- RUNNING 和 IDLE 两个 agent → count=2
- COMPLETED 状态的 agent 不出现（registry 自动驱逐）
- lastActivityAt 不为 null 时正确序列化为字符串
- 返回的 JSON map 包含 "agents" 和 "count" 键

### 约束
- 使用 JUnit 5
- 测试前后清理 AgentHealthRegistry.global()
- 不依赖 Spring 上下文（单元测试，直接 new KairoAgentsEndpoint()）
- 不修改 kairo-api/
