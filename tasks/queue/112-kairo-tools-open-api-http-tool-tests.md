状态: DONE
模块: kairo-tools
标题: OpenApiHttpTool 单元测试

目标:
先读取 OpenApiHttpTool 完整源码，再补充测试。

测试场景（按实际 API 确定）:
- 读取源码确认类结构和可测方法
- 工具名称、描述等基本属性
- 输入参数校验
- 基本调用不抛异常

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/openapi/OpenApiHttpToolTest.java

约束:
- 不修改 kairo-api/
- 先读取完整源码再决定测试场景
