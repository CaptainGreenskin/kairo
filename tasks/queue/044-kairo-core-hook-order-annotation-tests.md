状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Hook 顺序正确性）

## 目标

为 `DefaultHookChain` 的 `order` 属性排序逻辑添加测试，
验证多个 handler 按 order 值从小到大执行。

## 背景

Hook 系统支持 `@PreReasoning(order=X)` 等 order 属性，
但没有专门测试 order 排序逻辑是否正确（低 order 值先执行）。

## 需要实现

### 测试：HookOrderingTest.java（4+ 用例）

- 多个 handler 注册顺序任意，但 order 值决定执行顺序
- order=1 先于 order=2 先于 order=10
- 相同 order 值按注册顺序执行
- 负数 order 值有效（先于 order=0）

## 验收标准

- [ ] 4+ 测试通过
- [ ] `mvn test -pl kairo-core -Dtest=HookOrderingTest` 通过

## Agent 可以自主完成

YES
