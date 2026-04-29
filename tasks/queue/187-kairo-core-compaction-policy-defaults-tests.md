状态: DONE
模块: kairo-core
标题: CompactionPolicyDefaults 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- 默认策略配置是否合理
- 各阶段压缩配置的正确性

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/CompactionPolicyDefaultsTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
