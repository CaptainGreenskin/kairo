状态: DONE
模块: kairo-core
标题: SessionMetadata + SessionSnapshot 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- SessionMetadata: 字段存储、builder 构建、incrementIteration()
- SessionSnapshot: 序列化/反序列化（如果有）、字段存储

新增文件:
- kairo-core/src/test/java/io/kairo/core/session/SessionMetadataTest.java
- kairo-core/src/test/java/io/kairo/core/session/SessionSnapshotTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
