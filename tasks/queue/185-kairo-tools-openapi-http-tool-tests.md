状态: DONE
模块: kairo-tools
标题: OpenApiHttpTool 单元测试

目标:
先读取完整源码，补充单元测试。

测试场景（按实际 API 确定）:
- 工具名称、描述、参数 schema 正确
- 成功执行 HTTP 请求返回响应
- 请求失败时返回错误信息

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/openapi/OpenApiHttpToolTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
