状态: IN_PROGRESS
创建时间: 2026-04-26
优先级: P1（M6：新工具）

## 目标

在 `kairo-tools` 添加 `WebFetchTool`，让 Agent 能 HTTP GET 读取网页内容。

## 背景

当前 kairo-tools 没有 HTTP 获取工具。编程 Agent 经常需要获取文档、API 
响应或网页内容。Java 17+ 内置 `HttpClient`，无需新增外部依赖。

## 需要实现

### WebFetchTool.java

`kairo-tools/src/main/java/io/kairo/tools/info/WebFetchTool.java`

```java
@Tool(name="web_fetch", description="Fetch content from a URL via HTTP GET", 
      category=ToolCategory.INFORMATION, sideEffect=ToolSideEffect.READ_ONLY)
```

参数：
- `url`（required）：完整 URL，必须以 http:// 或 https:// 开头
- `timeoutSeconds`（optional，默认 30）：超时秒数
- `maxBytes`（optional，默认 512_000）：最大响应体字节数

实现要点：
- 用 `java.net.http.HttpClient` + `HttpRequest` + `HttpResponse`
- Content-Type 非 text/* 时返回错误（"URL returns non-text content: {contentType}"）
- 响应超过 maxBytes 时截断并在末尾加 "\n... (truncated)"
- User-Agent: "Kairo-Agent/1.x"
- 不允许访问 localhost / 127.* / 192.168.* / 10.* / 172.16-31.*（SSRF 防护）

### WebFetchToolTest.java

`kairo-tools/src/test/java/io/kairo/tools/info/WebFetchToolTest.java`

使用 `com.sun.net.httpserver.HttpServer`（JDK 内置）启动本地测试服务器（绑定
到 `localhost:0` 随机端口），验证：
- 正常 GET 返回 200 文本内容
- 非 http/https URL 返回错误
- localhost URL 被 SSRF 防护拒绝（返回 isError=true）
- 超时场景（服务端延迟响应）返回错误
- 响应超过 maxBytes 时内容被截断

注意：测试服务器绑定到 localhost，所以测试中要绕过 SSRF 检查（使用构造器注入
allowLocalhost=true 标志，或直接在测试中验证错误信息）。

## 验收标准

- [ ] `WebFetchTool` 编译通过，不新增任何外部 Maven 依赖
- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-tools` 通过
- [ ] `mvn spotless:apply` 格式无问题

## Agent 可以自主完成

YES
