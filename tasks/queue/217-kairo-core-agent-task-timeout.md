状态: DONE
模块: kairo-core
标题: AgentTask + 任务级超时与取消

目标:
实现任务级超时和取消机制。Agent 运行超过配置时长时优雅中止，
并通过 OnError hook 通知。支持外部主动取消。

## 需要实现

`io.kairo.core.agent.AgentTaskOptions`
- record: maxDuration(Duration默认30min), onTimeout(Runnable)

`io.kairo.core.agent.AgentTaskHandle`
- 持有 Future<AgentResponse>
- cancel(): 中止 agent 执行
- isDone(), isRunning()

`io.kairo.core.agent.AgentTaskScheduler`
- submit(Agent, AgentRequest, AgentTaskOptions) → AgentTaskHandle
- 内部用 VirtualThreadPerTaskExecutor（JDK 21）或 Executors.newCachedThreadPool
- 超时通过 Future.get(timeout) + 中断实现
- 超时时触发 IterationGuards.abortRequested() 标记

修改 `DefaultReActAgent.ReActLoop`：
- 每轮迭代检查中断标志（Thread.interrupted() 或 AtomicBoolean）
- 被中断时优雅返回当前状态

### 约束
- 不修改 kairo-api/
- 使用 JDK 21 虚拟线程（JDK 17 兜底：平台线程）
- 不引入外部依赖
