状态: DONE
模块: kairo-core
标题: HybridThreshold + IterationGuards 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- HybridThreshold: contextWindow=0 时退化为 pressure 模式、contextWindow>0 时取 min(percentage, absolute)
- HybridThreshold: usedTokens=0 + pressure>0 时从 pressure 推算 usedTokens
- IterationGuards: 超出最大迭代次数时抛异常、正常迭代不抛

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/compaction/HybridThresholdTest.java
- kairo-core/src/test/java/io/kairo/core/agent/IterationGuardsTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
