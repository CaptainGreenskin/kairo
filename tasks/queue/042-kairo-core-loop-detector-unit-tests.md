状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：测试覆盖率提升）

## 目标

为 `LoopDetector` 添加完整单元测试，验证哈希检测和频率检测的边界行为。

## 背景

`LoopDetector` 是防止 Agent 无限循环的核心机制，但测试覆盖不足。
`LoopDetectorTest.java` 已存在，但需要补充边界用例。

## 需要实现

### 1. 补充测试（在现有 LoopDetectorTest.java 中添加，或新建）

验证：
- 哈希警告阈值触发（warn 但不终止）
- 哈希硬限制触发（返回 HARD_STOP）
- 频率警告阈值触发
- 频率硬限制触发
- 滑动窗口超出范围后频率降为0
- 哈希检测 + 频率检测同时触发时的行为

## 验收标准

- [ ] 6+ 新测试用例覆盖上述场景
- [ ] `mvn test -pl kairo-core -Dtest=LoopDetectorTest` 通过

## Agent 可以自主完成

YES
