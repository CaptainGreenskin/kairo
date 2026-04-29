状态: DONE
模块: kairo-mcp
标题: McpSecurityPolicy + McpToolGroup 单元测试

目标:
先读取两个类源码，补充基本测试。

测试场景（按实际 API 确定）:
- McpSecurityPolicy: 构造/工厂，allow/deny 逻辑（按实际 API）
- McpToolGroup: 构造不抛异常，属性可读，工具列表操作

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/McpSecurityPolicyTest.java
- kairo-mcp/src/test/java/io/kairo/mcp/McpToolGroupTest.java
  或合并为一个文件

约束:
- 不修改 kairo-api/
- 先读完整源码
- kairo-mcp 只有 JUnit5 + Mockito（无 AssertJ）
