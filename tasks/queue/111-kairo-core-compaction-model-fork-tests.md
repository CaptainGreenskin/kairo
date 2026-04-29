状态: DONE
模块: kairo-core
标题: CompactionModelFork 单元测试

目标:
先读取 CompactionModelFork 完整源码，再补充测试。

测试场景（按实际 API 确定）:
- 构造后不抛异常
- summarize() 返回非 null Mono
- summarize() 空消息列表不抛异常
- 返回值来自 delegate（用 stub ModelProvider 验证调用发生）

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/CompactionModelForkTest.java

约束:
- 不修改 kairo-api/
- 先读取完整源码再决定测试场景
- stub ModelProvider 必须实现 name()、call()、stream() 三个方法
