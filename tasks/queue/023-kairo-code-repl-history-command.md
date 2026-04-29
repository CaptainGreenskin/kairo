状态: DONE
创建时间: 2026-04-26
优先级: P2（M5 REPL 完善：会话历史）

## 目标

在 REPL 中添加 `:history` 命令，显示当前会话的对话历史（用户/助手消息列表）。

## 背景

用户在长会话中无法回顾之前的对话，`:history` 通过
`agent.snapshot().conversationHistory()` 显示历史记录。

## 需要实现

### 1. HistoryCommand.java

```java
public class HistoryCommand implements SlashCommand {
    @Override public String name() { return "history"; }
    @Override public String description() { return "Show conversation history"; }
    @Override public void execute(String args, ReplContext context) {
        var history = context.agent().snapshot().conversationHistory();
        // print each message with role prefix
    }
}
```

### 2. 注册到 ReplLoop.createCommandRegistry()

### 3. 测试：HistoryCommandTest.java（3+ 用例）

## 验收标准

- [ ] REPL 中 `:history` 显示对话历史
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES
