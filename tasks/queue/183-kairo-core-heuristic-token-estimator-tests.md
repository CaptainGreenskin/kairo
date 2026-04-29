状态: DONE
模块: kairo-core
标题: HeuristicTokenEstimator 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- 空消息列表返回 0
- 单条消息估算 token 数
- 多条消息累加
- 包含 ToolUseContent/ToolResultContent 的消息估算

新增文件:
- kairo-core/src/test/java/io/kairo/core/context/HeuristicTokenEstimatorTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
