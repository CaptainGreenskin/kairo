状态: TODO
优先级: P2
模块: kairo-tools
标题: HttpTool 单元测试（in-process HttpServer）
depends_on: 241-kairo-tools-http-tool

目标:
为 HttpTool 补充单元测试，使用 JDK com.sun.net.httpserver.HttpServer 在随机端口起
in-process 测试服务器，不依赖外部网络。

## 需要实现

`io.kairo.tools.info.HttpToolTest`（8+ 测试用例）

场景：
- GET 请求返回 200 body
- POST 请求含请求体
- 非 2xx 状态码 → isError=true
- 超时处理（timeoutSeconds=1，服务器故意延迟）
- 自定义请求头
- followRedirects=false 时不跟随 301
- 响应体超 100KB 时截断提示
- 元数据包含 statusCode/method/url

### 约束
- 不依赖外部网络（全部 in-process）
- 不 mock 具体类（Java 25 Byte Buddy 限制）
- 每个测试后关闭 HttpServer
