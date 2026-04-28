状态: DONE
模块: kairo-core
标题: D2 DistributedLeaderElection — 基于内存的 Leader 选举

目标:
实现轻量级 Leader 选举机制，确保分布式场景下只有一个节点运行排他任务（如 OutboxPoller）。
不引入外部依赖，使用内存+心跳实现，可替换为 JDBC/Redis 后端。

## 需要实现

`io.kairo.core.leader.LeaderElector`
- 接口：tryAcquire(leaseDuration): boolean, release(), isLeader(): boolean, refresh()

`io.kairo.core.leader.LeaseEntry`
- record: nodeId(String), acquiredAt(Instant), expiresAt(Instant)
- boolean isExpired()

`io.kairo.core.leader.LeaderStore`
- 内部接口（不进 kairo-api）
- tryAcquire(nodeId, leaseDuration): boolean
- release(nodeId)
- currentLease(): Optional<LeaseEntry>

`io.kairo.core.leader.InMemoryLeaderStore`
- AtomicReference<LeaseEntry> + compare-and-set 保证原子性
- 过期租约被视为空（tryAcquire 时检测并替换）

`io.kairo.core.leader.DefaultLeaderElector`
- 实现 LeaderElector + AutoCloseable
- ScheduledExecutorService 定期 refresh（默认每 leaseDuration/3）
- stop() 释放租约

### 约束
- 不修改 kairo-api/
- 不引入外部依赖
- 线程安全，CAS 语义
