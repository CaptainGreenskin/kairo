状态: DONE
模块: kairo-mcp
标题: McpStaticGuardrailPolicy 单元测试

目标:
先读取 McpStaticGuardrailPolicy 完整源码，补充测试。

测试场景（按实际 API 确定）:
- 读取源码确认接口和逻辑
- allow-list: 工具在列表中 → 允许，不在 → 拒绝
- deny-list: 工具在列表中 → 拒绝，不在 → 允许
- 空列表行为

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/McpStaticGuardrailPolicyTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
