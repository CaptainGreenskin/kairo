状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Hook 决策路由测试）

## 目标

为 `HookDecisionApplier` 添加单元测试，验证 CONTINUE / MODIFY /
SKIP / ABORT / INJECT 决策的路由逻辑。

## 背景

`HookDecisionApplier` 将 Hook 返回的决策值映射到实际行为
（继续、修改事件、跳过当前步骤、中止循环、注入消息）。
决策路由是 Hook 系统的核心，需要确保每种决策值行为正确。

## 需要实现

### 测试：HookDecisionApplierTest.java

先读取 `HookDecisionApplier.java` 理解其公开 API，然后验证：
- CONTINUE 决策不修改事件，允许继续
- MODIFY 决策使用修改后的事件值
- SKIP 决策跳过当前步骤（返回合适信号）
- ABORT 决策中止循环（抛出或返回 error Mono）

## 验收标准

- [ ] 4+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
