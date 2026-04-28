状态: TODO
模块: kairo-core
标题: AgentCallObserver — Agent 调用回调接口

目标:
为 DefaultReActAgent 添加可插拔的调用回调接口，让 kairo-observability
等模块能注册 Micrometer 指标采集，而不在 kairo-core 引入 Micrometer 依赖。

## 需要实现

`io.kairo.core.health.AgentCallObserver`（接口）：
```java
public interface AgentCallObserver {
    void onCallStart(String agentId, String agentName);
    void onCallEnd(String agentId, String agentName, Duration duration, boolean success);

    static void setGlobal(AgentCallObserver observer) { Holder.INSTANCE.set(observer); }
    static AgentCallObserver global() { return Holder.INSTANCE.get(); }

    final class Holder {
        private static final AtomicReference<AgentCallObserver> INSTANCE = new AtomicReference<>();
    }
}
```

修改 `DefaultReActAgent`：
- call() 开始时：`AgentCallObserver obs = AgentCallObserver.global(); if (obs != null) obs.onCallStart(id, name)`
- call() doFinally：`if (obs != null) obs.onCallEnd(id, name, duration, success)`

### 约束
- 接口在 kairo-core，不依赖 Micrometer
- AtomicReference 而非 volatile（接口字段不能是 volatile）
- 不修改 kairo-api/
