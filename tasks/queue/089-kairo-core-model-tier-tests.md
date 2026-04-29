状态: DONE
模块: kairo-core
标题: ModelTier record 单元测试

目标:
为 kairo-core 的 ModelTier record 补充单元测试。

测试场景:
- 正常构造，字段访问返回正确值
- models 集合被防御性复制（不可变）
- tierName 为 null 时抛 NullPointerException
- models 为 null 时抛 NullPointerException
- costPerInputToken 为 null 时抛 NullPointerException
- costPerOutputToken 为 null 时抛 NullPointerException
- expectedLatency 为 null 时抛 NullPointerException
- record equality（相同字段 → 相等）
- toString 包含 tierName

新增文件:
- kairo-core/src/test/java/io/kairo/core/routing/ModelTierTest.java

约束:
- 不修改 kairo-api/
