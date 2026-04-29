状态: DONE
模块: kairo-mcp
标题: McpSecurityPolicy + McpToolGroup + Elicitation Handler 单元测试

目标:
先读取类的完整源码，补充测试。

测试场景（按实际 API 确定）:
- McpSecurityPolicy: enum 值存在，每个策略行为
- McpToolGroup: 构建、添加工具、按名称查找
- AutoApproveElicitationHandler: 自动批准所有请求
- AutoDeclineElicitationHandler: 自动拒绝所有请求
- DevOnlyAutoApproveHandler: 仅在 dev 模式下批准

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/McpSecurityPolicyTest.java
- kairo-mcp/src/test/java/io/kairo/mcp/McpToolGroupTest.java
- kairo-mcp/src/test/java/io/kairo/mcp/AutoApproveElicitationHandlerTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
- kairo-mcp 只有 JUnit5 + Mockito（无 AssertJ）
