# 任务 241：HttpTool — 轻量 HTTP 客户端工具

> **给 Qoder**：把整段（从这里到底）复制进 Qoder 对话框，让它在 `/Users/liulihan/IdeaProjects/sre/claude/kairo` 下完成。完成后告诉我（Claude Code），我做验收。

---

## 上下文

仓库：`/Users/liulihan/IdeaProjects/sre/claude/kairo`（Java 17 / Maven 多模块）
目标分支：**`feature/qoder-241-http-tool`**（请先 `git checkout -b feature/qoder-241-http-tool main`，不要在 main 改）

## 实现目标

新建 `kairo-tools/src/main/java/io/kairo/tools/info/HttpTool.java`，让 Agent 能直接调用外部 HTTP API（用 Java 11+ `java.net.http.HttpClient`，**禁止引入 OkHttp / Apache HttpClient**）。

## 必须满足

- 类签名：`@Tool(name="http_request", category=ToolCategory.INFORMATION, sideEffect=ToolSideEffect.WRITE) public class HttpTool implements ToolHandler`
- 参数（用 `@ToolParam`）：
  | 参数 | 必填 | 默认 | 说明 |
  |---|---|---|---|
  | `url` | ✅ | — | 请求 URL |
  | `method` | ❌ | `GET` | GET/POST/PUT/DELETE/PATCH/HEAD |
  | `headers` | ❌ | `{}` | JSON 对象，额外请求头 |
  | `body` | ❌ | `""` | 请求体字符串 |
  | `timeoutSeconds` | ❌ | `30` | 超时秒数 |
  | `followRedirects` | ❌ | `true` | 是否跟随 3xx |
- 响应体最大 100KB，超过截断 + 在 metadata 标 `truncated=true`
- 非 2xx 状态码返回 `ToolResult.error(...)`（`isError=true`），但 body 仍要带回
- ToolResult metadata 必须含：`statusCode`、`method`、`url`、`headers`（响应头）、`truncated`、`readOnly=false`
- SSRF 防护：复用 `WebFetchTool` 同包内已有的 BLOCKED_HOST_PREFIXES / BLOCKED_HOST_RANGES_172 黑名单（提取成共用常量或工具类，**不要重复**）

## 参考实现（必看）

`kairo-tools/src/main/java/io/kairo/tools/info/WebFetchTool.java` —— 同包同风格，直接照着改。SSRF 黑名单逻辑、`HttpClient` 用法、ToolResult 构造都能抄。

## 测试

`kairo-tools/src/test/java/io/kairo/tools/info/HttpToolTest.java`：
- GET 200 正常路径
- POST 带 body
- 自定义 headers 透传
- 超时（用很短 timeout + 慢 endpoint mock）
- 非 2xx 返回 isError=true 但带 body
- 超过 100KB 截断
- SSRF 拦截（`http://127.0.0.1` / `http://localhost`）

用 `WireMock` 或 JDK 自带 `HttpServer`（项目已有 `WebFetchToolTest` 看用哪个）。**不允许**真实发外网请求。

## 验证命令

```bash
cd /Users/liulihan/IdeaProjects/sre/claude/kairo
mvn spotless:apply -pl kairo-tools
mvn verify -pl kairo-tools -am
```

两个命令都必须 0 错误。

## 提交

```bash
git add kairo-tools/
git commit -m "feat(kairo-tools): add HttpTool for arbitrary HTTP requests"
git push -u origin feature/qoder-241-http-tool
```

不要：
- 修改 `kairo-api/`
- 引入新依赖（除非 `kairo-bom` 已经管着）
- `--no-verify` / `-DskipTests` / `--force`

## 完成后

回到 Claude Code 这边，告诉我"qoder 241 完成，分支 feature/qoder-241-http-tool"，我会跑 mvn verify + diff review + 评分。
