状态: DONE
模块: kairo-core
标题: SnipCompaction + MicroCompaction 单元测试

目标:
先读取两个压缩类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- SnipCompaction: compact() 从末尾截断消息到目标 token 数
- SnipCompaction: 边界条件（空列表、已在目标内）
- MicroCompaction: compact() 保留系统消息，压缩旧消息
- MicroCompaction: 最小保留条数

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/SnipCompactionTest.java
- kairo-core/src/test/java/io/kairo/core/context/compaction/MicroCompactionTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认 compact() 方法签名
