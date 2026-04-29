状态: DONE
模块: kairo-mcp
标题: ElicitationRequest / ElicitationResponse / ElicitationAction 单元测试

目标:
先读取源码，为 MCP elicitation 相关简单类型补充测试。

测试场景（按实际 API 确定）:
- 读取并确认类结构
- accessors 正常返回
- equals/hashCode（若为 record）
- enum 常量（若 ElicitationAction 是枚举）

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/ElicitationTypesTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码再决定测试场景
