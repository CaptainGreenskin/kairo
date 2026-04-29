状态: DONE
模块: kairo-spring-boot-starter-core
标题: AgentActuatorAutoConfiguration 端点测试

目标:
为 AgentActuatorAutoConfiguration 添加测试，覆盖 /actuator/agent 端点
以及 AgentProgressEndpoint 和 AgentMetricsEndpoint（如已在代码中存在）。

背景:
AgentActuatorAutoConfiguration 在 Spring Boot 应用有 Agent bean 时自动注册
/actuator/agent 端点。目前没有测试验证该端点的注册和响应内容。

## 需要实现

### 测试文件
`kairo-spring-boot-starter-core/src/test/java/io/kairo/spring/AgentActuatorAutoConfigurationTest.java`

测试场景（共 8+ 个）：
- 有 Agent bean 时注册 AgentEndpoint
- 无 Agent bean 时不注册（NegativeTest）
- @ConditionalOnMissingBean 允许用户提供自定义 AgentEndpoint
- AgentEndpoint.info() 返回正确字段（name, state, tools, toolCount）
- toolCount 与 tools 列表长度一致
- state 字段不为 null
- tools 字段是 List 类型

提示：
- 使用 ApplicationContextRunner 测试 AutoConfiguration
- 用 mock Agent 和 mock ToolRegistry

约束:
- 不修改 kairo-api/
- 不新增外部依赖
