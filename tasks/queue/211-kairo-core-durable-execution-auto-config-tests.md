状态: DONE
模块: kairo-spring-boot-starter-core
标题: DurableExecutionAutoConfiguration 测试

目标:
为 DurableExecutionAutoConfiguration 添加完整测试，覆盖 enabled/disabled 及属性绑定。

背景:
DurableExecutionAutoConfiguration 控制持久化执行存储的注册，但测试只覆盖 JDBC store。
缺少对 auto-config 条件逻辑的测试。

## 需要实现

`kairo-spring-boot-starter-core/src/test/java/io/kairo/spring/execution/DurableExecutionAutoConfigurationTest.java`

测试场景（共 8+ 个）：
- 默认配置注册 InMemoryDurableExecutionStore
- enabled=false 时不注册任何 store
- @ConditionalOnMissingBean 允许用户提供自定义 store
- 属性绑定 kairo.durable-execution.enabled
- DurableExecutionProperties 默认值正确
- JDBC 数据源存在时注册 JdbcDurableExecutionStore
- JDBC 数据源不存在时 fallback 到 InMemory

约束:
- 不修改 kairo-api/
- 不新增外部依赖
