状态: DONE
优先级: P2
模块: kairo-tools
标题: WebSearchTool 单元测试

目标:
为 WebSearchTool 编写单元测试。WebSearchTool 使用 Tavily Search API，
需要 mock HTTP 客户端进行测试（不发真实网络请求）。

## 需要实现

`io.kairo.tools.info.WebSearchToolTest`（10+ 测试用例）

场景（使用内嵌 HttpServer 或 mock HttpClient）：
- 正常搜索返回结果列表（标题、URL、内容片段）
- query 参数缺失或空 → isError=true
- API key 未配置时返回清晰错误信息 → isError=true
- Tavily 服务返回 401（未授权）→ isError=true
- Tavily 服务返回 429（限流）→ isError=true，metadata 含 statusCode
- Tavily 服务返回空结果数组时：output 说明无结果，isError=false
- maxResults 参数限制结果数量
- 搜索结果正确解析：title, url, content 字段映射
- 请求超时（searchDepth 参数）被尊重
- 非 JSON 响应体 → isError=true

### 约束
- 使用 JDK com.sun.net.httpserver.HttpServer 模拟 Tavily 端点（随机端口）
- 不发真实网络请求
- TAVILY_API_KEY 通过 ToolContext 注入（mock），不依赖环境变量
- 不修改 kairo-api/
