状态: DONE
模块: kairo-core
标题: CompactionPolicyDefaults 单元测试

目标:
为 CompactionPolicyDefaults 常量类补充测试。

测试场景:
- PRESSURE_THRESHOLD > 0 且 <= 1
- PIPELINE_CIRCUIT_BREAKER_THRESHOLD > 0
- PIPELINE_CIRCUIT_BREAKER_COOLDOWN_SECONDS > 0
- 构造函数为 private（无法实例化）
- 与 CompactionThresholds 保持一致

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/CompactionPolicyDefaultsTest.java

约束:
- 不修改 kairo-api/
- 先读取 CompactionThresholds 确认常量值
