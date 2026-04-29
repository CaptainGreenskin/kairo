状态: DONE
模块: kairo-evolution
标题: EvolutionProvenance + EvolutionSignal + EvolutionState 单元测试

目标:
先读取三个类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- EvolutionProvenance: 来源信息字段（source/timestamp/traceId）
- EvolutionSignal: 信号类型、强度、消息
- EvolutionState: 状态转换，isTerminal()

新增文件:
- kairo-evolution/src/test/java/io/kairo/evolution/EvolutionProvenanceTest.java
- kairo-evolution/src/test/java/io/kairo/evolution/EvolutionSignalTest.java
- kairo-evolution/src/test/java/io/kairo/evolution/EvolutionStateTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
