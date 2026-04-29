状态: DONE
创建时间: 2026-04-26
优先级: P2（M5 REPL 完善：会话重置）

## 目标

在 REPL 中添加 `:reset` 命令，完全重置 Agent 会话（等同于重新启动），
包括清除对话历史和重新创建 Agent 实例。

## 背景

`:clear` 已有（清除历史），但用户有时需要完全重置所有状态（包括 Agent 内部状态）。
`:reset` 调用 `context.rebuildSession()` 重新创建 Agent。

## 需要实现

### 1. ResetCommand.java

```java
public class ResetCommand implements SlashCommand {
    @Override public String name() { return "reset"; }
    @Override public String description() { return "Reset agent session completely"; }
    @Override public void execute(String args, ReplContext context) {
        context.rebuildSession(opts -> opts);  // rebuild with same options
        context.writer().println("✓ Session reset.");
    }
}
```

### 2. 注册到 ReplLoop.createCommandRegistry()

### 3. 测试：ResetCommandTest.java（3+ 用例）

## 验收标准

- [ ] `:reset` 重建 Agent 会话
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES
