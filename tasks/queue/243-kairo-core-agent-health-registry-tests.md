状态: IN_PROGRESS
模块: kairo-core
标题: AgentHealthRegistry 单元测试

目标:
为 AgentHealthRegistry 和 AgentHealthInfo 补充完整单元测试。

## 需要实现

`io.kairo.core.health.AgentHealthRegistryTest`（10+ 测试用例）

场景：
- register + snapshot 返回已注册 agent
- snapshot 自动驱逐 COMPLETED 状态的 agent
- snapshot 自动驱逐 FAILED 状态的 agent
- snapshot 自动驱逐 supplier 抛异常的 agent
- deregister 后 snapshot 不再包含该 agent
- deregisterAll 清空所有
- 并发注册多个 agent，snapshot 包含所有
- AgentHealthInfo record 字段正确
- 空 registry 返回空 list
- IDLE/RUNNING/SUSPENDED 不被自动驱逐

### 约束
- 使用 JUnit 5
- 每个测试后清理 global registry（避免测试间状态污染）
- 不修改 kairo-api/
