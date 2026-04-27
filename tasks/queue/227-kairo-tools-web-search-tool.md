状态: DONE
模块: kairo-tools
标题: WebSearchTool — Tavily API 网页搜索工具

目标:
实现 WebSearchTool，让 Agent 能执行网页搜索并获取结构化结果。
使用 Tavily Search API（https://api.tavily.com/search）。

## 需要实现

`io.kairo.tools.info.WebSearchTool`
- @Tool(name="web_search", sideEffect=READ_ONLY)
- 参数:
  - query（required）: 搜索关键词
  - maxResults（optional, 默认 5）: 返回结果数
  - includeAnswer（optional, 默认 true）: 是否包含 AI 摘要答案
- 行为:
  1. 从 ToolContext.dependencies("TAVILY_API_KEY") → 环境变量 TAVILY_API_KEY
  2. POST https://api.tavily.com/search，body: {api_key, query, max_results, include_answer}
  3. 解析响应 JSON: {answer, results: [{title, url, content, score}]}
  4. 返回 answer + 格式化后的 results 列表
- 超时：10 秒
- 无 API key 时返回 error ToolResult（提示设置 TAVILY_API_KEY）

### 约束
- 不修改 kairo-api/
- 不引入新 HTTP 库（使用 java.net.http.HttpClient）
- 使用 Jackson 解析 JSON（已有依赖）
