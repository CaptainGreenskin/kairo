状态: IN_PROGRESS
创建时间: 2026-04-26
优先级: P1（M6：新工具）

## 目标

在 `kairo-tools` 添加 `TodoWriteTool`，让 Agent 能管理结构化待办事项列表。

## 背景

编程 Agent 经常需要追踪任务进度。TodoWrite 是 claude-code 的核心工具之一，
Kairo 框架应该提供等价实现。todo 列表存储在 agent 工作目录的 `.kairo/todos.json`。

## 需要实现

### TodoWriteTool.java

`kairo-tools/src/main/java/io/kairo/tools/agent/TodoWriteTool.java`

```java
@Tool(name="todo_write", description="Create or update the todo list. Replaces entire list with provided todos.",
      category=ToolCategory.AGENT, sideEffect=ToolSideEffect.LOCAL_FILE_CHANGE)
```

数据模型（内嵌类）：
```java
public record TodoItem(String id, String content, String status, String priority) {}
// status: "pending" | "in_progress" | "completed"
// priority: "high" | "medium" | "low"
```

参数：
- `todos`（required）：JSON 数组字符串，替换整个列表

存储：写入 workspace root 下的 `.kairo/todos.json`（自动创建目录）。

返回：成功时返回 "Wrote N todos"，失败时返回错误信息。

### TodoReadTool.java

`kairo-tools/src/main/java/io/kairo/tools/agent/TodoReadTool.java`

```java
@Tool(name="todo_read", description="Read the current todo list.", 
      category=ToolCategory.AGENT, sideEffect=ToolSideEffect.READ_ONLY)
```

返回：todos.json 内容，若文件不存在返回空列表 "[]"。

### 测试：TodoToolTest.java

`kairo-tools/src/test/java/io/kairo/tools/agent/TodoToolTest.java`

使用 `@TempDir` 和模拟 workspace，验证：
- write 创建 .kairo/todos.json
- read 返回写入的内容
- write 空列表清空文件
- write 非法 JSON 返回错误
- read 文件不存在返回 "[]"

## 验收标准

- [ ] `TodoWriteTool` + `TodoReadTool` 编译通过
- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-tools` 通过

## Agent 可以自主完成

YES
