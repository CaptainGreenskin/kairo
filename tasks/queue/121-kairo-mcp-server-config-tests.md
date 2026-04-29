状态: DONE
模块: kairo-mcp
标题: McpServerConfig 单元测试

目标:
先读取 McpServerConfig 源码，补充测试。

测试场景（按实际 API 确定）:
- 读取源码确认类结构（record / builder / POJO）
- 基本属性读取
- 构造或 builder 不抛异常

新增文件:
- kairo-mcp/src/test/java/io/kairo/mcp/McpServerConfigTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码再决定测试场景
