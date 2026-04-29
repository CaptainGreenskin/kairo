状态: DONE
模块: kairo-spring-boot-starter-evolution, kairo-spring-boot-starter-mcp
标题: Evolution 和 MCP Spring Boot 配置类单元测试

目标:
先读取完整源码，为两个 starter 的配置属性类补充单元测试。

背景:
kairo-spring-boot-starter-evolution 和 kairo-spring-boot-starter-mcp 有配置属性类，
目前没有专属单元测试。属性的默认值、setter/getter 需要验证。

测试场景（evolution）:
- EvolutionProperties 默认值正确（enabled, scheduleIntervalSeconds 等）
- 可以覆盖所有属性值

测试场景（mcp）:
- McpProperties 默认值正确（serverConfigs 为空列表等）
- 服务器配置列表可以追加

新增文件:
- kairo-spring-boot-starter-evolution/src/test/java/io/kairo/spring/evolution/EvolutionPropertiesTest.java
- kairo-spring-boot-starter-mcp/src/test/java/io/kairo/spring/mcp/McpPropertiesTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认属性类存在和默认值
