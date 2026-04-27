状态: DONE
模块: kairo-core
标题: Agent 生命周期完整去注册 — COMPLETED/FAILED 时从 AgentHealthRegistry 移除

目标:
补全 DefaultReActAgent 的健康注册生命周期。
当前只有 interrupt() 会 deregister，COMPLETED/FAILED 状态转换不会。

## 需要实现

修改 `DefaultReActAgent`：
- 在 ReAct 循环结束（state → COMPLETED 或 FAILED）时调用
  `AgentHealthRegistry.global().deregister(this.id)`
- 添加 `@PreDestroy` 方法作为兜底（Spring 容器销毁时也 deregister）

修改 `AgentHealthRegistry`：
- 新增 `deregisterAll()` 方法（用于测试和 Spring context 关闭）
- snapshot() 中若 supplier.get() 返回 COMPLETED/FAILED 状态，
  可选择性自动清理（configurable: kairo.health.auto-evict-terminal=true）

### 约束
- 不修改 kairo-api/
- deregister 必须幂等（多次调用无副作用）
- 不影响已有 interrupt() 逻辑
