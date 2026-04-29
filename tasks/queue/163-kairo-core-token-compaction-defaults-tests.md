状态: DONE
模块: kairo-core
标题: HeuristicTokenEstimator + CompactionPolicyDefaults 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- HeuristicTokenEstimator: 单消息估算、多消息批量、空列表
- CompactionPolicyDefaults: 常量值非 0、与 CompactionThresholds 一致

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/HeuristicTokenEstimatorTest.java
- kairo-core/src/test/java/io/kairo/core/context/CompactionPolicyDefaultsTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
