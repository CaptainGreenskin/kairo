状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：CLI 控制）

## 目标

在 `KairoCodeMain` 中添加 `--max-iterations` CLI 选项，允许用户控制
Agent 的最大迭代次数（覆盖 `AgentConfig` 默认值）。

## 背景

当前最大迭代次数固定在配置中。用户无法通过命令行控制，导致长任务时 Agent
提前停止或短任务时浪费迭代预算。

## 需要实现

### 1. KairoCodeMain.java

```java
@Option(
    names = "--max-iterations",
    description = "Maximum reasoning iterations (default: from config)",
    defaultValue = "-1")
private int maxIterations;
```

在构建 `CodeAgentConfig` 时，若 `maxIterations > 0` 则用此值覆盖默认值。

### 2. 测试：MaxIterationsOptionTest.java（3+ 用例）

验证：
- 默认值 -1 时不覆盖配置
- 正值时传入 config
- 0 或负值时忽略（或报错）

## 验收标准

- [ ] `--max-iterations 5` 生效
- [ ] 3+ 测试通过
- [ ] kairo-code 编译通过

## Agent 可以自主完成

YES
