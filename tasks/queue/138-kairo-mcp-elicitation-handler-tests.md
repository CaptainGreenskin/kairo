状态: DONE
模块: kairo-mcp
标题: AutoApproveElicitationHandler + AutoDeclineElicitationHandler + DevOnlyAutoApproveHandler 单元测试

目标:
先读取三个 handler 类源码，补充基本测试。

测试场景（按实际 API 确定）:
- AutoApproveElicitationHandler: 始终返回 accept() 响应
- AutoDeclineElicitationHandler: 始终返回 decline() 响应
- DevOnlyAutoApproveHandler: 根据条件批准/拒绝

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/ElicitationHandlersTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码确认 API 结构
- kairo-mcp 只有 JUnit5 + Mockito（无 AssertJ）
