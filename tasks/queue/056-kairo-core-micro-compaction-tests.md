状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Level-2 压缩策略测试）

## 目标

为 `MicroCompaction` 添加单元测试，验证 Level-2 压缩
（清除 tool result 详细内容，保留结构）的触发和压缩行为。

## 背景

`MicroCompaction` 在 85% 压力时触发，将 tool result 的详细内容
替换为 `[Result: toolUseId - status - N bytes]` 格式的摘要，
同时保留最近 3 条消息不压缩。

## 需要实现

先读取 `MicroCompaction.java` 理解接口，然后编写：

### 测试：MicroCompactionTest.java

验证：
- `shouldTrigger` 在高压力时返回 true，低压力时返回 false
- `compact` 保留非 tool result 消息不变
- `compact` 将 tool result 内容替换为摘要格式
- 摘要格式包含 toolUseId、status（success/error）、bytes
- 最近 3 条消息的 tool result 也被压缩（MicroCompaction 压缩全部）
- `name()` 返回 "micro"，`priority()` 返回 200

## 验收标准

- [ ] 6+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
