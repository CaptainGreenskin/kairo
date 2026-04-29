状态: DONE
模块: kairo-core
标题: TokenEstimator 接口契约测试

目标:
先读取 TokenEstimator 接口和 HeuristicTokenEstimator，补充接口契约验证测试。

测试场景:
- 空消息列表返回 0
- 单消息估算返回 >= 0
- 消息越长估算值越大（单调性）
- 多条消息比一条多（累加性）

已有文件（扩展）:
- kairo-core/src/test/java/io/kairo/core/context/HeuristicTokenEstimatorTest.java
  → 检查是否已覆盖上述场景，若未覆盖则新增测试方法

或新增文件:
- kairo-core/src/test/java/io/kairo/core/context/TokenEstimatorContractTest.java

约束:
- 不修改 kairo-api/
- 先读取 TokenEstimator 接口确认方法签名
