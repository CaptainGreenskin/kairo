状态: DONE
模块: kairo-core
标题: CollapseCompaction / MicroCompaction / AutoCompaction 单元测试

目标:
先读取三个 Compaction 实现类完整源码，补充基本测试。

测试场景（按实际 API 确定）:
- 实现 ContextCompactionStrategy 接口（如适用）
- 构造不抛异常
- compress/apply 对空上下文不抛异常
- name/priority 等属性可读

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/CollapseCompactionTest.java
- kairo-core/src/test/java/io/kairo/core/context/compaction/MicroCompactionTest.java
- kairo-core/src/test/java/io/kairo/core/context/compaction/AutoCompactionTest.java
  或合并为 CompactionStrategyTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认类结构和包路径
