状态: TODO
模块: kairo-tools
标题: HttpTool 单元测试

目标:
为 HttpTool 补充单元测试，使用内嵌 HTTP 服务器验证各种场景。

## 需要实现

`io.kairo.tools.info.HttpToolTest`（10+ 个测试用例）

场景（使用 com.sun.net.httpserver.HttpServer 内嵌服务器）：
- GET 请求返回 200，验证响应体
- POST 请求携带 body，验证服务端收到
- 自定义请求头被发送
- 404 状态码 → isError=true
- 500 状态码 → isError=true
- 响应体超过 100KB 时截断
- URL 无效 → isError=true
- 不支持的 HTTP 方法 → isError=true
- method 默认为 GET
- timeoutSeconds 参数被尊重（使用延迟响应验证）

### 约束
- 使用 JDK 内置 com.sun.net.httpserver（无额外依赖）
- 不修改 kairo-api/
