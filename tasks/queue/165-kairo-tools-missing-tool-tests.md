状态: DONE
模块: kairo-tools
标题: 缺失工具类测试（EnterPlanModeTool、TeamCreateTool、TeamDeleteTool、OpenApiHttpTool）

目标:
kairo-tools 仍缺少以下工具的 Test 文件（分支上有但尚未合并到 main）。
先读取完整源码，重新补充测试。

测试场景（按实际 API 确定）:
- EnterPlanModeTool: @Tool 注解、execute() 返回 "Entered Plan Mode"
- TeamCreateTool: 缺少 name 参数时 isError()、正常创建调用 teamManager.create()
- TeamDeleteTool: 缺少 name 参数时 isError()、正常删除调用 teamManager.delete()
- OpenApiHttpTool: GET 请求、路径参数替换、POST 请求、IOException 错误

新增文件:
- kairo-tools/src/test/java/io/kairo/tools/agent/EnterPlanModeToolTest.java
- kairo-tools/src/test/java/io/kairo/tools/agent/TeamCreateToolTest.java
- kairo-tools/src/test/java/io/kairo/tools/agent/TeamDeleteToolTest.java
- kairo-tools/src/test/java/io/kairo/tools/openapi/OpenApiHttpToolTest.java

约束:
- 不修改 kairo-api/
- 先读完整源码
- OpenApiHttpTool 用 FakeHttpClient extends HttpClient 替代 mock（ByteBuddy 限制）
- TeamCreateTool/TeamDeleteTool 的 Team 返回值不需要 mock
