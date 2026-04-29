状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：ReAct 循环守卫测试）

## 目标

为 `IterationGuards` 添加单元测试，验证各种守卫条件的触发逻辑：
最大 iteration 限制、token 预算耗尽、中断信号、shutdown 信号。

## 背景

`IterationGuards` 是 ReAct 循环的安全阀，负责在以下情况下
终止循环并返回最终响应：iteration 超限、token 耗尽、
外部中断、系统 shutdown。核心逻辑无专门测试。

## 需要实现

### 测试：IterationGuardsTest.java

验证：
- `evaluate()` 在 iteration >= maxIterations 时返回非空 Msg
- `evaluate()` 在 token 预算耗尽时返回非空 Msg
- `evaluate()` 在 interrupted=true 时返回非空 Msg
- `evaluate()` 在所有守卫未触发时返回 Mono.empty()
- `checkCancelled()` 在 interrupted=true 时返回 error

注意：`IterationGuards` 是 package-private，测试类放在
`io.kairo.core.agent` 包下。

## 验收标准

- [ ] 5+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
