状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：REPL 可观测性）

## 目标

在 REPL 中添加 `:hooks` 命令，列出当前 session 中注册的所有 Hook Handler 及其相位。

## 背景

用户需要知道哪些 Hook 处于活动状态以便调试。依赖任务 035（需要
`DefaultHookChain.getRegisteredHandlers()` 方法）。

## 需要实现

### 1. HooksCommand.java（新文件）

```java
public class HooksCommand implements SlashCommand {
    @Override public String name() { return "hooks"; }
    @Override public String description() { return "List registered hook handlers"; }
    @Override public void execute(String args, ReplContext context) {
        // 从 context 中获取 hookChain
        // 遍历已注册的 handler，打印类名
        // 无 handler 时打印 "No hook handlers registered"
    }
}
```

### 2. ReplContext.java 添加 hookChain 访问

添加字段和 getter（类型用 Object，运行时 cast 到 DefaultHookChain）。

### 3. 测试：HooksCommandTest.java（3+ 用例）

验证：
- 无 hook 时输出提示信息
- 有 hook 时输出类名列表
- 命令名称为 "hooks"

## 验收标准

- [ ] `:hooks` 打印已注册 Handler 列表
- [ ] 3+ 测试通过
- [ ] kairo-code 编译通过

## Agent 可以自主完成

YES（依赖任务 035 先完成）
