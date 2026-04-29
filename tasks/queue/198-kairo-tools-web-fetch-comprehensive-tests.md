状态: DONE
模块: kairo-tools
标题: WebFetchTool 综合测试（含 JDK HttpServer 真实 HTTP 测试）

目标:
先读取 WebFetchToolTest.java 完整内容，检查覆盖是否充分；
若不足，补充使用 JDK 内置 HttpServer 的真实 HTTP 测试。

背景:
WebFetchTool 已实现，但测试可能缺少真实 HTTP 场景验证（非 mock）。
JDK 内置 com.sun.net.httpserver.HttpServer 可绑定到随机端口做集成测试，无需额外依赖。

需要验证的场景:
- 正常 200 GET 返回文本内容
- 404 响应时 isError=true
- Content-Type 非文本（application/octet-stream）时返回错误
- 响应超过 maxBytes 时内容被截断（末尾含 "truncated"）
- 非 http/https URL 立即返回错误（不发起网络请求）
- SSRF 防护：localhost URL 被拒绝（allowLocalhost=false）
- timeoutSeconds 参数覆盖默认值
- 缺少 url 参数时返回参数错误

修改或新增:
- kairo-tools/src/test/java/io/kairo/tools/info/WebFetchToolTest.java（追加场景）

约束:
- 不修改 kairo-api/
- 不新增外部依赖（只用 JDK 内置 com.sun.net.httpserver）
- 先读完整源码
