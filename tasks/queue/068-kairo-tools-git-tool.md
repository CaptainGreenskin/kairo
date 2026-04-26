状态: IN_PROGRESS
创建时间: 2026-04-26
优先级: P1（M6：新工具）

## 目标

在 `kairo-tools` 添加 `GitTool`，让编程 Agent 能执行 git 操作。

## 背景

代码 Agent 必须能读取 git 状态、查看 diff、查看日志。这是 M6 目标
"kairo-code 开发 Kairo" 的核心能力需求。GitTool 通过复用 `BashTool` 的
`LocalProcessSandbox` 执行 git 命令，无需新增依赖。

## 需要实现

### GitTool.java

`kairo-tools/src/main/java/io/kairo/tools/exec/GitTool.java`

```java
@Tool(name="git", description="Execute a git command in the workspace",
      category=ToolCategory.EXECUTION, sideEffect=ToolSideEffect.LOCAL_FILE_CHANGE)
```

参数：
- `subcommand`（required）：git 子命令，如 "status", "diff", "log --oneline -10"
- `workingDirectory`（optional）：工作目录，默认为 workspace root

安全约束（防止危险操作）：
- 禁止：push --force、reset --hard、clean -f、branch -D（这些操作不可逆）
- 禁止：checkout -- （丢弃工作区修改）
- 违规时返回 isError=true + 说明信息

实现：拼接 `git {subcommand}` 通过 LocalProcessSandbox 执行（与 BashTool 相似）

### GitToolTest.java

`kairo-tools/src/test/java/io/kairo/tools/exec/GitToolTest.java`

使用 `@TempDir` 初始化真实 git 仓库（`git init`），验证：
- `git status` 返回仓库状态
- `git log` 在空仓库或有提交时正常返回
- 危险命令（push --force）被拦截返回错误
- 无效目录返回错误

## 验收标准

- [ ] `GitTool` 编译通过，不新增外部依赖
- [ ] 4+ 测试通过
- [ ] `mvn test -pl kairo-tools` 通过

## Agent 可以自主完成

YES
