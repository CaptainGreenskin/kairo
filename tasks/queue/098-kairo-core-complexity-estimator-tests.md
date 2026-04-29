状态: DONE
模块: kairo-core
标题: ComplexityEstimator 单元测试

目标:
先读取 ComplexityEstimator 类（kairo-core/.../model/anthropic/ComplexityEstimator.java），
再补充单元测试。

测试场景（按实际 API 确定）:
- 空消息列表 → 低复杂度
- 短消息 → 低复杂度
- 很长的消息 → 高复杂度
- 多工具调用 → 提升复杂度
- 返回值在合理范围内

新增文件:
- kairo-core/src/test/java/io/kairo/core/model/anthropic/ComplexityEstimatorTest.java

约束:
- 不修改 kairo-api/
- 先读取源码确认实际逻辑
