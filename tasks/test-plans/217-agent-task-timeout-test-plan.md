# Test Plan: Task 217 — AgentTask + Timeout/Cancellation

## Setup

```java
// Minimal stub agent
Agent fastAgent = /* agent that returns "done" instantly */;
Agent slowAgent = /* agent that sleeps 10 seconds */;
AgentTaskScheduler scheduler = new AgentTaskScheduler(Executors.newCachedThreadPool());
```

---

## AgentTaskOptions

### T1: defaults() produces 30-minute timeout, no callback
```java
AgentTaskOptions opts = AgentTaskOptions.defaults();
assertThat(opts.maxDuration()).isEqualTo(Duration.ofMinutes(30));
assertThat(opts.onTimeout()).isNull();
```

### T2: withTimeout(Duration) sets maxDuration, no callback
```java
AgentTaskOptions opts = AgentTaskOptions.withTimeout(Duration.ofSeconds(5));
assertThat(opts.maxDuration()).isEqualTo(Duration.ofSeconds(5));
assertThat(opts.onTimeout()).isNull();
```

### T3: withTimeout(Duration, Runnable) stores callback
```java
AtomicBoolean fired = new AtomicBoolean();
AgentTaskOptions opts = AgentTaskOptions.withTimeout(Duration.ofSeconds(1), () -> fired.set(true));
assertThat(opts.onTimeout()).isNotNull();
```

### T4: zero or negative duration → IllegalArgumentException
```java
assertThatThrownBy(() -> new AgentTaskOptions(Duration.ZERO, null))
    .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> new AgentTaskOptions(Duration.ofSeconds(-1), null))
    .isInstanceOf(IllegalArgumentException.class);
```

---

## AgentTaskHandle

### T5: isDone() / isRunning() reflect task state
```java
AgentTaskHandle handle = scheduler.submit(fastAgent, Msg.user("hi"));
handle.get(5, TimeUnit.SECONDS);  // wait for completion
assertThat(handle.isDone()).isTrue();
assertThat(handle.isRunning()).isFalse();
```

### T6: cancel() calls agent.interrupt() and cancels future
```java
Agent mockAgent = mock(Agent.class);
when(mockAgent.call(any())).thenReturn(Mono.never());
AgentTaskHandle handle = scheduler.submit(mockAgent, Msg.user("hi"));
handle.cancel();
assertThat(handle.isDone()).isTrue();
verify(mockAgent).interrupt();
```

### T7: get() returns agent response on success
```java
Msg expected = Msg.assistant("done");
// fastAgent.call() returns Mono.just(expected)
AgentTaskHandle handle = scheduler.submit(fastAgent, Msg.user("go"));
Msg result = handle.get(5, TimeUnit.SECONDS);
assertThat(result).isEqualTo(expected);
```

---

## AgentTaskScheduler

### T8: submit() returns running handle immediately (non-blocking)
```java
AgentTaskHandle handle = scheduler.submit(slowAgent, Msg.user("go"));
assertThat(handle.isRunning()).isTrue();
handle.cancel();  // cleanup
```

### T9: timeout fires → agent.interrupt() called, onTimeout callback invoked
```java
AtomicBoolean callbackFired = new AtomicBoolean();
AgentTaskOptions opts = AgentTaskOptions.withTimeout(
    Duration.ofMillis(200),
    () -> callbackFired.set(true));

Agent blockingAgent = /* agent whose Mono.block() waits forever */;
AgentTaskHandle handle = scheduler.submit(blockingAgent, Msg.user("go"), opts);

Thread.sleep(500);  // wait for watchdog
assertThat(callbackFired.get()).isTrue();
assertThat(handle.isDone()).isTrue();
```

### T10: fast task completes before timeout — watchdog does not fire
```java
AtomicBoolean fired = new AtomicBoolean();
AgentTaskOptions opts = AgentTaskOptions.withTimeout(
    Duration.ofSeconds(10),  // generous timeout
    () -> fired.set(true));

AgentTaskHandle handle = scheduler.submit(fastAgent, Msg.user("go"), opts);
handle.get(2, TimeUnit.SECONDS);
Thread.sleep(100);  // let watchdog settle
assertThat(fired.get()).isFalse();  // watchdog cancelled early
```

### T11: shutdown() — subsequent submit throws RejectedExecutionException
```java
scheduler.shutdown();
assertThatThrownBy(() -> scheduler.submit(fastAgent, Msg.user("go")))
    .isInstanceOf(RejectedExecutionException.class);
```

### T12: try-with-resources (AutoCloseable)
```java
try (AgentTaskScheduler s = new AgentTaskScheduler()) {
    AgentTaskHandle h = s.submit(fastAgent, Msg.user("go"));
    h.get(2, TimeUnit.SECONDS);
}
// no leak assertions
```
