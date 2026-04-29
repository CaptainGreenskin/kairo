状态: DONE
模块: kairo-core
标题: CompactionTrigger 单元测试

目标:
先读取 CompactionTrigger 类（kairo-core/src/main/java/io/kairo/core/agent/CompactionTrigger.java），
再补充单元测试。

测试场景（按实际 API 确定）:
- 如果是 enum：枚举值数量、名称稳定性、valueOf 未知值
- 如果是 record/class：字段访问、equals、null 参数

新增文件:
- kairo-core/src/test/java/io/kairo/core/agent/CompactionTriggerTest.java

约束:
- 不修改 kairo-api/
- 先读取源码确认 API 再写测试
