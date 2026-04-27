状态: TODO
模块: kairo-spring-boot-starter-multi-agent
标题: Multi-Agent Spring Boot 自动配置综合测试

目标:
为 kairo-spring-boot-starter-multi-agent 补充完整的自动配置测试，
验证多智能体协调器的 bean 创建、条件注册、属性绑定。

背景:
kairo-spring-boot-starter-multi-agent 目前没有专属测试文件。
TeamCoordinator、InProcessMessageBus、DefaultTeamManager 的自动配置
需要验证在不同条件下能正确创建或跳过。

## 需要实现

### 测试文件
`kairo-spring-boot-starter-multi-agent/src/test/java/io/kairo/spring/MultiAgentAutoConfigurationTest.java`

场景:
- 默认配置下 InProcessMessageBus bean 创建
- 禁用多智能体时 TeamManager bean 不创建（kairo.multi-agent.enabled=false）
- 自定义 MessageBus bean 时自动配置退避
- DefaultTaskDispatchCoordinator bean 正确初始化
- A2A 配置属性绑定正确

`kairo-spring-boot-starter-multi-agent/src/test/java/io/kairo/spring/MultiAgentPropertiesTest.java`

场景:
- MultiAgentProperties 默认值验证
- 属性 setter/getter 正确

共 12+ 测试

约束:
- 不修改 kairo-api/
- 先读完整源码确认属性类存在
