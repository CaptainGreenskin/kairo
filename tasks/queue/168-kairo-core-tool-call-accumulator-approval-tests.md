状态: DONE
模块: kairo-core
标题: ToolCallAccumulator + ToolApprovalFlow 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- ToolCallAccumulator: accumulate() 聚合 tool_use block、reset() 清空、pending() 返回未完成列表
- ToolApprovalFlow: ALLOW 决策放行、DENY 决策返回错误 ToolResult

新增文件:
- kairo-core/src/test/java/io/kairo/core/execution/ToolCallAccumulatorTest.java
- kairo-core/src/test/java/io/kairo/core/execution/ToolApprovalFlowTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
