状态: DONE
模块: kairo-core
标题: SessionMetadata + SessionSnapshot 值对象测试

目标:
为 kairo-core 的两个会话值对象补充单元测试：
- SessionMetadata (record: sessionId, createdAt, updatedAt, turnCount)
- SessionSnapshot (record: sessionId, createdAt, turnCount, messages, agentState)

新增文件:
- kairo-core/src/test/java/io/kairo/core/session/SessionMetadataTest.java
- kairo-core/src/test/java/io/kairo/core/session/SessionSnapshotTest.java

测试场景:
SessionMetadata:
1. 构造器字段访问正确
2. equals / hashCode（两个相同值的实例相等）
3. toString 包含 sessionId
4. 不同 turnCount 不相等

SessionSnapshot:
1. 构造器字段访问正确
2. messages 不可修改（通过 List.copyOf 或 List.of 验证）
3. equals / hashCode
4. toString 包含 sessionId

约束:
- 不修改 kairo-api/
