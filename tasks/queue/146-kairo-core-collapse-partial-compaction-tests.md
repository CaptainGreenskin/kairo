状态: DONE
模块: kairo-core
标题: CollapseCompaction + PartialCompaction 单元测试

目标:
先读取两个压缩类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- CollapseCompaction: 将多条消息折叠成摘要消息
- CollapseCompaction: 保留系统消息，折叠其余
- PartialCompaction: 局部压缩（仅压缩部分消息）
- 边界条件：空列表、单条消息

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/CollapseCompactionTest.java
- kairo-core/src/test/java/io/kairo/core/context/compaction/PartialCompactionTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
