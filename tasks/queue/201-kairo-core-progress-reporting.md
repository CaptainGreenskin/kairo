状态: IN_PROGRESS
模块: kairo-core, kairo-spring-boot-starter-core
标题: Agent 进度上报机制（M4 完成标志）

目标:
实现完整的 Agent 进度上报机制，让外部系统能实时感知 Agent 执行进度。

背景:
M4 目标「进度上报」目前缺失。Agent 执行时外部调用方无法知道当前第几步、
完成百分比、当前在做什么。ExecutionEventEmitter 已有基础，但进度语义未实现。

## 需要实现

### 1. ProgressSnapshot（kairo-core）
`kairo-core/src/main/java/io/kairo/core/agent/ProgressSnapshot.java`
```java
public record ProgressSnapshot(
    int currentIteration,
    int maxIterations,
    int percentage,        // 0-100
    String currentActivity,
    long elapsedMs,
    int toolCallsCount,
    long tokensUsed
) {
    public static ProgressSnapshot initial(int maxIterations) {...}
    public ProgressSnapshot advance(String activity, int toolCalls, long tokens) {...}
}
```

### 2. AgentProgressTracker（kairo-core）
`kairo-core/src/main/java/io/kairo/core/agent/AgentProgressTracker.java`
- 线程安全的进度跟踪器（AtomicReference<ProgressSnapshot>）
- `update(iteration, activity, toolCalls, tokens)` 方法
- `getSnapshot()` 返回最新进度
- ReActLoop 在每次迭代后调用 update

### 3. AgentProgressEndpoint（kairo-spring-boot-starter-core）
`kairo-spring-boot-starter-core/src/main/java/io/kairo/spring/AgentProgressEndpoint.java`
- Spring Boot Actuator 自定义 endpoint：`/actuator/agent-progress`
- 返回 JSON：当前所有 active agent 的进度快照
- `@Endpoint(id = "agent-progress")`

### 4. 集成 ReActLoop
- ReActLoop 在每次迭代完成时更新 AgentProgressTracker
- AgentProgressTracker 注册到 Spring context（通过 AgentActuatorAutoConfiguration）

### 测试
- `ProgressSnapshotTest`：进度计算（percentage、advance 方法）
- `AgentProgressTrackerTest`：线程安全更新、并发场景
- `AgentProgressEndpointTest`：endpoint 返回正确 JSON
- 共 15+ 测试

约束:
- 不修改 kairo-api/
- ProgressSnapshot 和 AgentProgressTracker 在 kairo-core
- Endpoint 在 kairo-spring-boot-starter-core
