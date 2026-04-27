状态: DONE
模块: kairo-core
标题: AgentCallObserver — Agent 调用回调接口

目标:
提供可插拔的 Agent 调用回调接口，让 kairo-observability 等模块能注册
Micrometer 指标采集，而不在 kairo-core 引入 Micrometer 依赖。

## 需要实现

`io.kairo.core.health.AgentCallObserver`（接口）：
- void onCallStart(String agentId, String agentName)
- void onCallEnd(String agentId, String agentName, Duration duration, boolean success)
- static void setGlobal(AgentCallObserver) — 通过 Holder.INSTANCE（AtomicReference）
- static AgentCallObserver global() — 返回当前全局实例
- final class Holder { AtomicReference<AgentCallObserver> INSTANCE }（注：接口字段不能是 volatile）

修改 `DefaultReActAgent`：
- call() 开始前（state=RUNNING 后）调用 observer.onCallStart
- call() 的 doFinally 中调用 observer.onCallEnd（duration=now-sessionStartTime, success=COMPLETED）

### 约束
- kairo-core 不引入 Micrometer
- 使用 AtomicReference，不用 volatile（接口字段隐式 final）
- 不修改 kairo-api/
