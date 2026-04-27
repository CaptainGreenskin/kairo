# Test Plan: Task 218 — D2 DistributedLeaderElection

## Setup

```java
InMemoryLeaderStore store = new InMemoryLeaderStore();
Duration lease = Duration.ofSeconds(2);
```

---

## LeaseEntry

### T1: isExpired() false for future expiry
```java
LeaseEntry e = new LeaseEntry("node-1", Instant.now(), Instant.now().plusSeconds(60));
assertThat(e.isExpired()).isFalse();
```

### T2: isExpired() true for past expiry
```java
LeaseEntry e = new LeaseEntry("node-1", Instant.now().minusSeconds(10), Instant.now().minusSeconds(1));
assertThat(e.isExpired()).isTrue();
```

---

## InMemoryLeaderStore

### T3: first node acquires lease
```java
assertThat(store.tryAcquire("A", lease)).isTrue();
assertThat(store.currentLease()).isPresent()
    .map(l -> l.nodeId()).hasValue("A");
```

### T4: second node cannot acquire while first holds valid lease
```java
store.tryAcquire("A", lease);
assertThat(store.tryAcquire("B", lease)).isFalse();
```

### T5: same node can renew
```java
store.tryAcquire("A", lease);
assertThat(store.tryAcquire("A", lease)).isTrue(); // renew
```

### T6: release allows another node to acquire
```java
store.tryAcquire("A", lease);
store.release("A");
assertThat(store.tryAcquire("B", lease)).isTrue();
```

### T7: expired lease can be stolen
```java
Duration shortLease = Duration.ofMillis(100);
store.tryAcquire("A", shortLease);
Thread.sleep(150); // let lease expire
assertThat(store.tryAcquire("B", shortLease)).isTrue();
assertThat(store.currentLease().map(l -> l.nodeId())).hasValue("B");
```

### T8: release no-op when not holder
```java
store.tryAcquire("A", lease);
assertThatCode(() -> store.release("B")).doesNotThrowAnyException();
assertThat(store.currentLease().map(l -> l.nodeId())).hasValue("A"); // unchanged
```

---

## DefaultLeaderElector

### T9: tryAcquire delegates to store
```java
DefaultLeaderElector elector = new DefaultLeaderElector("n1", store, lease);
assertThat(elector.tryAcquire()).isTrue();
assertThat(elector.isLeader()).isTrue();
```

### T10: release gives up leadership
```java
DefaultLeaderElector elector = new DefaultLeaderElector("n1", store, lease);
elector.tryAcquire();
elector.release();
assertThat(elector.isLeader()).isFalse();
```

### T11: competing nodes — only one wins
```java
DefaultLeaderElector e1 = new DefaultLeaderElector("n1", store, lease);
DefaultLeaderElector e2 = new DefaultLeaderElector("n2", store, lease);
e1.tryAcquire();
assertThat(e1.isLeader()).isTrue();
assertThat(e2.isLeader()).isFalse();
assertThat(e2.tryAcquire()).isFalse();
```

### T12: start() + auto-renewal keeps lease alive beyond initial duration
```java
Duration shortLease = Duration.ofMillis(300);
DefaultLeaderElector elector = new DefaultLeaderElector("n1", store, shortLease);
elector.start();
Thread.sleep(800); // 2+ lease periods — renewal should keep it alive
assertThat(elector.isLeader()).isTrue();
elector.stop();
```

### T13: stop() releases lease
```java
DefaultLeaderElector elector = new DefaultLeaderElector("n1", store, lease);
elector.start();
elector.stop();
assertThat(elector.isLeader()).isFalse();
```

### T14: try-with-resources closes cleanly
```java
try (DefaultLeaderElector elector = new DefaultLeaderElector("n1", store, lease)) {
    elector.start();
    assertThat(elector.isLeader()).isTrue();
}
// after close: lease released
assertThat(store.currentLease()).isEmpty();
```
