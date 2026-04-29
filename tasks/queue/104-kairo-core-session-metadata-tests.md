状态: DONE
模块: kairo-core
标题: SessionMetadata 单元测试

目标:
为 kairo-core/.../session/SessionMetadata.java（record 类型）补充测试。

测试场景:
- 构造并访问 sessionId
- 构造并访问 createdAt
- 构造并访问 updatedAt
- 构造并访问 turnCount
- 相同字段的两个实例 equals()
- 字段不同时 not equals
- toString() 包含 sessionId

新增文件:
- kairo-core/src/test/java/io/kairo/core/session/SessionMetadataTest.java

约束:
- 不修改 kairo-api/
