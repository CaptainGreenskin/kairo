状态: DONE
模块: kairo-tools
标题: OpenApiHttpTool 单元测试

目标:
先读取 OpenApiHttpTool 完整源码，补充基本测试。

测试场景（按实际 API 确定）:
- @Tool 注解 name/description 不为 null
- 构造不抛异常
- execute() 缺少必填参数返回 error result
- 参数完整时不抛 NPE（可用 MockWebServer 或只测校验逻辑）

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/openapi/OpenApiHttpToolTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
