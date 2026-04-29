状态: DONE
模块: kairo-mcp
标题: MCP Elicitation Handler 类单元测试

目标:
先读取完整源码，为 AutoApproveElicitationHandler、AutoDeclineElicitationHandler、
ElicitationAction、ElicitationResponse 补充单元测试。

背景:
MCP Elicitation 是允许 MCP 服务端在工具执行中向用户请求额外信息的协议扩展。
目前这些处理器类没有专属测试。

测试场景:
- AutoApproveElicitationHandler 始终返回 ACCEPT 动作
- AutoDeclineElicitationHandler 始终返回 DECLINE 动作
- ElicitationAction 枚举值 (ACCEPT / DECLINE / CANCEL) 可序列化
- ElicitationResponse 包含正确的 action 和 data
- DevOnlyAutoApproveHandler 在开发环境返回 ACCEPT

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/ElicitationHandlerTest.java
- kairo-mcp/src/test/java/io/kairo/mcp/ElicitationResponseTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码再实现
