状态: DONE
模块: kairo-mcp
标题: McpSecurityPolicy + McpToolGroup 单元测试

目标:
为 kairo-mcp 模块补充以下类的单元测试：
- McpSecurityPolicy (enum: ALLOW_ALL/DENY_SAFE/DENY_ALL)
- McpToolGroup (ConcurrentHashMap; addTool, getServerName, getRegisteredToolNames)

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/McpSecurityPolicyTest.java
- kairo-mcp/src/test/java/io/kairo/mcp/McpToolGroupTest.java

约束:
- 不修改 kairo-api/
- McpToolGroup 测试覆盖并发安全场景（多线程 addTool）
- McpSecurityPolicy 测试覆盖 values(), name(), ordinal()
