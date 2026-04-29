状态: DONE
模块: kairo-core
标题: SessionMetadata + SessionSnapshot 单元测试

目标:
先读取两个 session 类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- SessionMetadata: 构造/工厂，属性可读，equals/hashCode（如为 record）
- SessionSnapshot: 构造/工厂，能序列化/反序列化，属性可读

新增文件:
- kairo-core/src/test/java/io/kairo/core/session/SessionMetadataTest.java
- kairo-core/src/test/java/io/kairo/core/session/SessionSnapshotTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
