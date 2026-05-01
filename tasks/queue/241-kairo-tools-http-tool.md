状态: DONE
模块: kairo-tools
标题: HttpTool — 轻量 HTTP 客户端工具

目标:
让 Agent 能直接调用外部 HTTP API，支持 REST API 测试、webhook 触发等场景。

## 需要实现

`io.kairo.tools.info.HttpTool`
- @Tool(name="http_request", sideEffect=WRITE)
- 参数:
  - url（required）: 请求 URL
  - method（optional, 默认 GET）: GET/POST/PUT/DELETE/PATCH/HEAD
  - headers（optional）: JSON 对象（额外请求头）
  - body（optional）: 请求体字符串
  - timeoutSeconds（optional, 默认 30）
  - followRedirects（optional, 默认 true）
- 使用 Java 11+ HttpClient（标准库）
- 响应体最大 100KB，超出截断并提示
- 非 2xx 状态码 isError=true
- 元数据：statusCode, method, url, headers, truncated, readOnly

### 约束
- 不修改 kairo-api/
- 不引入 okhttp/apache-httpclient
