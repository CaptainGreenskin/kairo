状态: DONE
模块: kairo-tools
标题: GithubTool — GitHub API 集成工具（Issues + PRs）

目标:
实现 GithubTool，让 Agent 能创建/查询 GitHub Issues 和 PRs。
这是 kairo-code 作为全自动开发 Agent 的核心能力。

## 需要实现

`io.kairo.tools.vcs.GithubTool`
- @Tool(name="github", sideEffect=WRITE)
- 参数：
  - action（required）: "create_issue" | "list_issues" | "create_pr" | "list_prs" | "get_pr" | "add_comment"
  - owner（required）: 仓库所有者
  - repo（required）: 仓库名
  - title: Issue/PR 标题
  - body: Issue/PR 内容
  - head: PR head branch
  - base: PR base branch（默认 main）
  - issue_number: issue 编号
  - state: "open" | "closed" | "all"
  - limit: 返回数量（默认 20）
- 通过 java.net.http.HttpClient 调用 GitHub REST API
- Token 从 GITHUB_TOKEN 环境变量读取
- 返回 JSON 字符串（parsed 为可读格式）

### 约束
- 不修改 kairo-api/
- 不引入 github-api / okhttp 依赖（只用 JDK HttpClient）
- 无 GITHUB_TOKEN 时返回明确错误（不抛 NPE）
- 支持 ToolContext 中的 token 注入（context.attribute("GITHUB_TOKEN")）
