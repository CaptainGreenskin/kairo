状态: DONE
优先级: P2
模块: kairo-core
标题: AgentCallObserver 单元测试

目标:
为 AgentCallObserver 接口和 DefaultReActAgent 集成补充单元测试。

## 需要实现

`io.kairo.core.health.AgentCallObserverTest`（6+ 测试用例）

场景：
- 默认全局实例为 NoopAgentCallObserver（无操作）
- setGlobal/global 正确切换实例
- DefaultReActAgent 成功完成 call() 后调用 onCallEnd(success=true)
- DefaultReActAgent 失败 call() 后调用 onCallEnd(success=false)
- onCallStart 在 call() 开始时被调用
- 并发 setGlobal 操作（AtomicReference 线程安全）

### 约束
- 使用 JUnit 5 + Mockito（接口 mock 合法）
- 测试后重置全局 observer（@AfterEach）
- 不修改 kairo-api/
