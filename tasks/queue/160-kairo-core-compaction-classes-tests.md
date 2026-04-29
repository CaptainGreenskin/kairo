状态: DONE
模块: kairo-core
标题: 压缩策略类单元测试（Snip/Micro/Collapse/Partial）

目标:
先读取 4 个压缩策略的完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- SnipCompaction: compress() 删除旧消息、保留系统提示
- MicroCompaction: compress() 生成摘要消息
- CollapseCompaction: compress() 合并连续同角色消息
- PartialCompaction: compress() 保留最近 N 条

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/SnipCompactionTest.java
- kairo-core/src/test/java/io/kairo/core/context/compaction/MicroCompactionTest.java
- kairo-core/src/test/java/io/kairo/core/context/compaction/CollapseCompactionTest.java
- kairo-core/src/test/java/io/kairo/core/context/compaction/PartialCompactionTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
- 压缩策略可能需要 ModelProvider mock（interfaces 可 mock）
