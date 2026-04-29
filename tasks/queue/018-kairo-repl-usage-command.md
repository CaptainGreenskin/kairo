状态: DONE
创建时间: 2026-04-26
优先级: P2（M4：REPL 能力完善）

## 目标

在 REPL 模式中添加 `:usage` 斜杠命令，显示当前会话的 token 消耗和迭代次数。

## 背景

`--show-usage` 在单次模式已实现。REPL 中用户无法感知消耗了多少 token，
`:usage` 命令通过 `agent.snapshot()` 给出实时统计。

## 需要实现

### 1. 在 CommandRegistry 注册 :usage 命令

```java
registry.register(":usage", session -> {
    AgentSnapshot snap = session.agent().snapshot();
    out.printf("total_tokens=%d  iterations=%d%n",
        snap.totalTokensUsed(), snap.iteration());
    return CommandResult.handled();
});
```

### 2. 测试：UsageCommandTest.java（至少 3 个用例）

- :usage 命令已注册
- :usage 命令输出包含 total_tokens
- :usage 命令输出包含 iterations

## 验收标准

- [ ] REPL 中 `:usage` 打印 token 统计
- [ ] 3+ 测试通过

## Agent 可以自主完成

YES

## 不需要修改 kairo-api SPI

YES
