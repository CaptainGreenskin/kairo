状态: DONE
模块: kairo-spring-boot-starter-core
标题: Spring Boot 配置属性类单元测试

目标:
先读取完整源码，为 ModelProperties、AgentProperties、ToolProperties 等配置属性类补充测试。

测试场景:
- 默认值是否合理
- 属性绑定是否正确
- 必填属性缺失时的验证

新增文件:
- kairo-spring-boot-starter-core/src/test/java/io/kairo/spring/config/ModelPropertiesTest.java
- kairo-spring-boot-starter-core/src/test/java/io/kairo/spring/config/AgentPropertiesTest.java
- kairo-spring-boot-starter-core/src/test/java/io/kairo/spring/config/ToolPropertiesTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
