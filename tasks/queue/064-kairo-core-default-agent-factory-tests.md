状态: DONE
创建时间: 2026-04-26
优先级: P2（M6：Agent 工厂测试）

## 目标

为 `DefaultAgentFactory` 添加单元测试，验证工厂方法创建
`DefaultReActAgent` 实例的行为。

## 背景

`DefaultAgentFactory` 实现 `AgentFactory` SPI，接受
`ToolExecutor`、`GracefulShutdownManager`、`GuardrailChain`
参数，创建并返回 `DefaultReActAgent` 实例。

## 需要实现

先读取 `DefaultAgentFactory.java` 和 `DefaultReActAgent.java`，
然后编写：

### 测试：DefaultAgentFactoryTest.java

验证：
- `createAgent(config)` 返回非 null Agent 实例
- 仅传 toolExecutor 的构造器能正常创建 agent
- 创建的 agent 能 `run()` 返回 Mono（至少能订阅不报错）
- 多次创建返回不同实例

## 验收标准

- [ ] 4+ 测试通过
- [ ] `mvn test -pl kairo-core` 通过

## Agent 可以自主完成

YES
