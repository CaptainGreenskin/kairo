状态: IN_PROGRESS
模块: kairo-core
标题: 将 DefaultReActAgent 接入 AgentHealthRegistry

目标:
AgentHealthRegistry 存在但为空，因为 DefaultReActAgent 从未注册自身。
实现接入后 /actuator/kairo-agents 才能真正返回运行中的 Agent 状态。

## 需要实现

修改 `io.kairo.core.agent.DefaultReActAgent`：

1. 构造函数末尾：
   ```java
   AgentHealthRegistry.global().register(
       this.id,
       () -> new AgentHealthInfo(this.id, this.name, this.state,
                                 this.currentIteration.get(), Instant.now())
   );
   ```

2. 在 call() 的 doFinally（COMPLETED/FAILED 状态后）deregister：
   ```java
   AgentHealthRegistry.global().deregister(this.id);
   ```

3. interrupt() 方法末尾：
   ```java
   AgentHealthRegistry.global().deregister(this.id);
   ```

### 约束
- 不修改 kairo-api/
- 导入 io.kairo.core.health.*，不要 io.kairo.api.*
- 不改变现有测试逻辑
