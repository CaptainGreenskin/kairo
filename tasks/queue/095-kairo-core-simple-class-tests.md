状态: DONE
模块: kairo-core
标题: LoopDetectionException + HeuristicTokenEstimator + CompactionPolicyDefaults 测试

目标:
为三个简单类补充单元测试（不依赖复杂外部状态）。

LoopDetectionException (kairo-core/.../agent/LoopDetectionException.java):
- 是 KairoException（RuntimeException）子类
- getMessage() 包含构造时的 message 参数
- 可被 RuntimeException catch

HeuristicTokenEstimator (kairo-core/.../context/HeuristicTokenEstimator.java):
- 空列表返回 0
- 单条消息：chars * 4/3（向下取整）
- 多条消息累加
- 实现 TokenEstimator 接口

CompactionPolicyDefaults (kairo-core/.../context/CompactionPolicyDefaults.java):
- PRESSURE_THRESHOLD 是 float，值 > 0 且 < 1
- PIPELINE_CIRCUIT_BREAKER_THRESHOLD 是正整数
- PIPELINE_CIRCUIT_BREAKER_COOLDOWN_SECONDS 是正数
- 不可实例化（私有构造器，反射时抛 IllegalAccessException）

新增文件:
- kairo-core/src/test/java/io/kairo/core/agent/LoopDetectionExceptionTest.java
- kairo-core/src/test/java/io/kairo/core/context/HeuristicTokenEstimatorTest.java
- kairo-core/src/test/java/io/kairo/core/context/CompactionPolicyDefaultsTest.java

约束:
- 不修改 kairo-api/
- HeuristicTokenEstimator.estimate() 需要 Msg 对象，先确认 Msg 的 text() 方法
