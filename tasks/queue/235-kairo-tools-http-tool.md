状态: IN_PROGRESS
模块: kairo-tools
标题: HttpTool — 轻量 HTTP 客户端工具

目标:
让 Agent 能直接调用外部 HTTP API，支持 REST API 测试、
webhook 触发、数据抓取等场景。

## 需要实现

`io.kairo.tools.info.HttpTool`
- @Tool(name="http_request", sideEffect=READ_ONLY 当 GET/HEAD，否则 WRITE)
- 参数:
  - url（required）: 请求 URL
  - method（optional, 默认 GET）: HTTP 方法（GET/POST/PUT/DELETE/PATCH/HEAD）
  - headers（optional）: JSON 对象，额外请求头
  - body（optional）: 请求体字符串（POST/PUT/PATCH 时）
  - timeoutSeconds（optional, 默认 30）: 超时时间
  - followRedirects（optional, 默认 true）: 是否跟随重定向
- 返回：状态码、响应头（精简版）、响应体（最大 100KB）
- 使用 Java 11+ HttpClient（标准库，无额外依赖）

### 约束
- 不修改 kairo-api/
- 不引入 okhttp/apache-httpclient
- 响应体超过 100KB 时截断并提示
- 非 2xx 状态码返回 isError=true 并包含状态码信息
