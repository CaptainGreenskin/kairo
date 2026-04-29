状态: DONE
创建时间: 2026-04-26
优先级: P2（M6 准备：REPL 工具可见性）

## 目标

在 REPL 中添加 `:tools` 命令，列出当前 Agent 会话中所有已注册的工具及其描述，
方便用户了解 Agent 的能力边界。

## 背景

M5 证明工具已注册，但用户在 REPL 中无法直接看到当前有哪些工具可用。
`:tools` 命令通过 `session.toolRegistry().getAll()` 读取并展示。

## 需要实现

### 1. ToolsCommand.java

```java
public class ToolsCommand implements SlashCommand {
    @Override public String name() { return "tools"; }
    @Override public String description() { return "List all registered tools"; }
    @Override public void execute(String args, ReplContext context) {
        var tools = context.session().toolRegistry().getAll();
        context.writer().printf("Registered tools (%d):%n", tools.size());
        tools.forEach(t -> context.writer().printf("  %-20s %s%n", t.name(), t.description()));
        context.writer().flush();
    }
}
```

### 2. 注册到 ReplLoop.createCommandRegistry()

### 3. 测试：ToolsCommandTest.java（3+ 用例）

## 验收标准

- [ ] `:tools` 输出所有注册工具
- [ ] 3+ 测试通过
- [ ] mvn test -pl kairo-code-cli 通过

## Agent 可以自主完成

YES
