状态: DONE
模块: kairo-core
标题: CompactionTrigger + CompactionModelFork 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- CompactionTrigger: shouldTrigger() 基于 ContextState、create() 工厂方法
- CompactionModelFork: 根据不同模型返回不同 CompactionStrategy 序列

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/CompactionTriggerTest.java
- kairo-core/src/test/java/io/kairo/core/context/CompactionModelForkTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
