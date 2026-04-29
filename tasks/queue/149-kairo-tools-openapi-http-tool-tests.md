状态: DONE
模块: kairo-tools
标题: OpenApiHttpTool 单元测试

目标:
先读取 OpenApiHttpTool 完整源码，补充测试。

测试场景（按实际 API 确定）:
- 构造函数 baseUrl trailing slash 处理
- execute() GET 请求路径参数替换
- execute() POST 请求携带 body
- execute() 查询参数附加
- 响应成功/失败处理

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/openapi/OpenApiHttpToolTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
- 使用 mock HttpClient 避免真实网络调用
