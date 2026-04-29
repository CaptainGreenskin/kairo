状态: DONE
模块: kairo-evolution
标题: InMemoryEvolutionRuntimeStateStore 和进化状态类单元测试

目标:
先读取完整源码，为 InMemoryEvolutionRuntimeStateStore、EvolutionState、EvolutionEventType 补充测试。

背景:
kairo-evolution 模块的运行时状态存储是核心组件，支持 M7 自进化目标。
目前状态存储类无专属测试。

测试场景:
- store 初始为空，getCounters(agentId) 返回零值
- increment 操作线程安全地累积计数
- 多个 agentId 互不干扰
- EvolutionState 枚举可正常使用
- EvolutionEventType 枚举覆盖所有已知事件类型

新增文件:
- kairo-evolution/src/test/java/io/kairo/evolution/InMemoryEvolutionRuntimeStateStoreTest.java
- kairo-evolution/src/test/java/io/kairo/evolution/EvolutionStateTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
