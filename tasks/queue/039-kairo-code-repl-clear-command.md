状态: DONE
创建时间: 2026-04-26
优先级: P3（M6：REPL 体验）

## 目标

在 REPL 中添加 `:clear` 命令，清除当前会话的对话历史，从空白上下文开始。

## 背景

长时间使用 REPL 后，上下文累积会导致 Token 消耗增大或响应变慢。
`:clear` 提供一个快速清除历史的方式，等同于重建 session 但不切换目录。

## 需要实现

### 1. ClearCommand.java（新文件）

```java
public class ClearCommand implements SlashCommand {
    @Override public String name() { return "clear"; }
    @Override public String description() { return "Clear conversation history"; }
    @Override public void execute(String args, ReplContext context) {
        context.rebuildSession(null);
        context.writer().println("Conversation history cleared.");
        context.writer().flush();
    }
}
```

### 2. ReplLoop.java 注册 ClearCommand

在 `SlashCommandRegistry` 中 `registry.register(new ClearCommand())` 。

### 3. 测试：ClearCommandTest.java（3+ 用例）

验证：
- 命令名为 "clear"
- execute 调用后输出清除提示
- rebuildSession 被调用

## 验收标准

- [ ] `:clear` 重置对话历史
- [ ] 3+ 测试通过
- [ ] kairo-code 编译通过

## Agent 可以自主完成

YES
