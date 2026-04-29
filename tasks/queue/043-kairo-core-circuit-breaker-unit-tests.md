状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：熔断器测试完整性）

## 目标

为 `kairo-core` 的 `CircuitBreaker`（模型/工具熔断器）补充单元测试，
覆盖 CLOSED → OPEN → HALF_OPEN 状态转换路径。

## 背景

熔断器是系统稳定性的核心保障，但现有测试（如果存在）可能不完整。
需验证状态机完整性。

## 需要实现

查找 CircuitBreaker 相关实现文件，为其状态机编写单元测试：
- CLOSED 状态下连续失败超阈值 → 转 OPEN
- OPEN 状态下请求被拒绝
- OPEN 等待 resetTimeout 后 → 转 HALF_OPEN
- HALF_OPEN 成功 → 回到 CLOSED
- HALF_OPEN 失败 → 回到 OPEN

## 验收标准

- [ ] 5+ 测试用例覆盖状态转换
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
