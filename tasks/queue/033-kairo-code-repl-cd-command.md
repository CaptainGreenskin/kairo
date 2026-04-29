状态: DONE
创建时间: 2026-04-26
优先级: P3（M6 准备：工作目录切换）

## 目标

在 REPL 中添加 `:cd <path>` 命令，允许用户在不重启的情况下切换 Agent 的工作目录。

## 背景

当用户想在同一个 REPL 会话中处理不同项目时，需要切换工作目录。
`:cd` 命令重建 CodeAgentConfig 中的 workingDir，并触发会话重建。

## 需要实现

### 1. CdCommand.java

```java
public class CdCommand implements SlashCommand {
    @Override public String name() { return "cd"; }
    @Override public String description() { return "Change working directory (rebuilds session)"; }
    @Override public void execute(String args, ReplContext context) {
        if (args == null || args.isBlank()) {
            context.writer().println("Usage: :cd <path>");
            return;
        }
        Path newDir = Path.of(args.trim()).toAbsolutePath();
        if (!Files.isDirectory(newDir)) {
            context.writer().printf("Error: not a directory: %s%n", newDir);
            return;
        }
        context.setWorkingDir(newDir.toString());
        context.writer().printf("Working directory: %s%n", newDir);
    }
}
```

### 2. ReplContext 添加 setWorkingDir(String) 方法

更新 config 并重建会话。

### 3. 测试：CdCommandTest.java（3+ 用例）

## 验收标准

- [ ] `:cd <path>` 切换工作目录
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES
