状态: DONE
模块: kairo-event-stream
标题: D3 TransactionalOutbox — 持久化事件外发保证

目标:
实现 Outbox 模式，让 KairoEvent 的发布先落盘（InMemoryOutboxStore），由后台
OutboxPoller 异步推送到下游 sink。保证进程崩溃不丢事件。

## 需要实现

`io.kairo.event.outbox.OutboxEntry`
- record: id(UUID), eventType(String), payload(byte[]), createdAt(Instant), status(PENDING/DELIVERED/FAILED), retries(int)

`io.kairo.event.outbox.OutboxStore`
- 内部接口（不进 kairo-api）
- save(OutboxEntry), pollPending(int limit), markDelivered(UUID), markFailed(UUID, String)

`io.kairo.event.outbox.InMemoryOutboxStore`
- ConcurrentLinkedDeque + ConcurrentHashMap 实现
- 线程安全；PENDING → DELIVERED/FAILED 状态机

`io.kairo.event.outbox.TransactionalOutboxPublisher`
- 包装现有 KairoEventBus
- publish 先 save(OutboxEntry)，再 publish 到 bus；bus 失败时保留 PENDING 状态

`io.kairo.event.outbox.OutboxPoller`
- 实现 AutoCloseable
- ScheduledExecutorService 每 100ms poll PENDING 条目，调用 bus 推送
- 最大重试 3 次，超过后 markFailed
- start()/stop() 生命周期

### 约束
- 不修改 kairo-api/
- 不引入外部依赖
- 与现有 DefaultKairoEventBus 解耦（通过组合）
