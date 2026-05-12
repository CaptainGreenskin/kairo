状态: DONE
优先级: P2
模块: kairo-tools
标题: GithubTool 单元测试

目标:
为 GithubTool 编写单元测试。GithubTool 通过 JDK HttpClient 调用 GitHub REST API，
支持 issues/PR/branch CRUD 操作，token 从 ToolContext 或环境变量获取。

## 需要实现

`io.kairo.tools.vcs.GithubToolTest`（10+ 测试用例）

场景（使用 JDK com.sun.net.httpserver.HttpServer 模拟 GitHub API）：
- list issues：返回 issue 列表（id, title, state）
- get issue：返回单个 issue 详情
- create issue：POST 正确 body，返回新建 issue
- close issue：PATCH state=closed，返回更新后 issue
- list PRs：返回 PR 列表
- get PR：返回单个 PR 详情（含 base/head branch）
- add comment：POST comment body，返回 comment
- GITHUB_TOKEN 未提供时 → isError=true（缺少认证）
- GitHub API 返回 404 → isError=true，metadata 含 statusCode
- GitHub API 返回 422（参数错误）→ isError=true
- action 参数无效时 → isError=true

### 约束
- 使用 JDK com.sun.net.httpserver.HttpServer（随机端口），覆盖 API base URL
- Token 通过 ToolContext attribute "GITHUB_TOKEN" 注入
- 不修改 kairo-api/
