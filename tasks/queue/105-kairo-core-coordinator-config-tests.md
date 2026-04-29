状态: DONE
模块: kairo-core
标题: CoordinatorConfig 单元测试

目标:
先读取 CoordinatorConfig 和 AgentConfig 类，再补充测试。

测试场景:
- of(baseConfig) 默认 maxConcurrentWorkers=5
- of(baseConfig) 默认 requirePlanBeforeDispatch=true
- of(baseConfig) 默认 workerTemplates 为空
- of(baseConfig, templates) 携带 workerTemplates
- maxConcurrentWorkers=0 抛 IllegalArgumentException
- maxConcurrentWorkers 负数抛 IllegalArgumentException
- builder: maxConcurrentWorkers 覆盖默认值
- builder: requirePlanBeforeDispatch=false
- record equals 和 hashCode

新增文件:
- kairo-core/src/test/java/io/kairo/core/agent/CoordinatorConfigTest.java

约束:
- 不修改 kairo-api/
- 先读取 AgentConfig 源码确认构造方式
