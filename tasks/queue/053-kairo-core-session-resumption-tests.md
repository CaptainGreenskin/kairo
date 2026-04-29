状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：会话恢复健壮性）

## 目标

为 `SessionResumption` 添加单元测试，验证会话历史注入逻辑。

## 背景

`SessionResumption` 从持久化存储加载上一次会话的消息历史，
并注入到 `ReActLoop`。是 Agent 长对话能力的基础。
需要验证：无 memoryStore 时 no-op、有历史时正确注入。

## 需要实现

### 测试：SessionResumptionTest.java

验证：
- `AgentConfig` 无 memoryStore 时 `loadSessionIfPresent()` 不注入消息
- `AgentConfig` 有 memoryStore 但无会话数据时不注入
- memoryStore 返回历史消息时，`loop.getHistory()` 包含这些消息

注意：`SessionResumption` 是 package-private，测试类放在
`io.kairo.core.agent` 包下。使用 Mockito mock MemoryStore。

## 验收标准

- [ ] 3+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
