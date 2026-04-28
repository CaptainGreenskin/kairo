# Test Plan: Task 214 — TransactionalOutbox

## Setup

```java
InMemoryOutboxStore store = new InMemoryOutboxStore();
// Capture published events
List<KairoEvent> published = new CopyOnWriteArrayList<>();
KairoEventBus fakeBus = new KairoEventBus() {
    public void publish(KairoEvent e) { published.add(e); }
    public Flux<KairoEvent> subscribe() { return Flux.empty(); }
    public Flux<KairoEvent> subscribe(String d) { return Flux.empty(); }
};
TransactionalOutboxPublisher publisher = new TransactionalOutboxPublisher(fakeBus, store);
```

---

## OutboxEntry

### T1: pending() creates PENDING entry with retries=0
```java
OutboxEntry e = OutboxEntry.pending("MY_EVENT", new byte[]{1,2,3});
assertThat(e.status()).isEqualTo(OutboxEntry.Status.PENDING);
assertThat(e.retries()).isEqualTo(0);
assertThat(e.eventType()).isEqualTo("MY_EVENT");
assertThat(e.payload()).containsExactly(1, 2, 3);
```

### T2: withStatus and incrementRetries produce new records
```java
OutboxEntry e = OutboxEntry.pending("T", new byte[0]);
OutboxEntry delivered = e.withStatus(OutboxEntry.Status.DELIVERED);
assertThat(delivered.status()).isEqualTo(OutboxEntry.Status.DELIVERED);
assertThat(delivered.id()).isEqualTo(e.id()); // same id

OutboxEntry retried = e.incrementRetries();
assertThat(retried.retries()).isEqualTo(1);
```

---

## InMemoryOutboxStore

### T3: save + pollPending returns entry
```java
OutboxEntry e = OutboxEntry.pending("T", new byte[0]);
store.save(e);
List<OutboxEntry> pending = store.pollPending(10);
assertThat(pending).hasSize(1);
assertThat(pending.get(0).id()).isEqualTo(e.id());
```

### T4: markDelivered removes from pollPending
```java
store.save(e);
store.markDelivered(e.id());
assertThat(store.pollPending(10)).isEmpty();
```

### T5: markFailed removes from pollPending
```java
store.save(e);
store.markFailed(e.id(), "boom");
assertThat(store.pollPending(10)).isEmpty();
```

### T6: pollPending respects limit
```java
for (int i = 0; i < 5; i++) store.save(OutboxEntry.pending("T", new byte[0]));
assertThat(store.pollPending(3)).hasSize(3);
```

### T7: pollPending skips non-PENDING entries
```java
OutboxEntry a = OutboxEntry.pending("T", new byte[0]);
OutboxEntry b = OutboxEntry.pending("T", new byte[0]);
store.save(a);
store.save(b);
store.markDelivered(a.id());
List<OutboxEntry> pending = store.pollPending(10);
assertThat(pending).hasSize(1);
assertThat(pending.get(0).id()).isEqualTo(b.id());
```

---

## TransactionalOutboxPublisher

### T8: publish — happy path: event delivered + entry DELIVERED
```java
KairoEvent event = KairoEvent.of("test", "FOO", Map.of());
publisher.publish(event);
assertThat(published).hasSize(1);
assertThat(store.pollPending(10)).isEmpty(); // entry marked delivered
```

### T9: publish — bus throws: entry stays PENDING for retry
```java
KairoEventBus failingBus = new KairoEventBus() {
    public void publish(KairoEvent e) { throw new RuntimeException("network down"); }
    public Flux<KairoEvent> subscribe() { return Flux.empty(); }
    public Flux<KairoEvent> subscribe(String d) { return Flux.empty(); }
};
TransactionalOutboxPublisher failingPublisher = new TransactionalOutboxPublisher(failingBus, store);
failingPublisher.publish(KairoEvent.of("test", "BAR", Map.of()));
assertThat(store.pollPending(10)).hasSize(1); // stays PENDING
```

---

## OutboxPoller

### T10: pollNow() delivers pending entries
```java
OutboxEntry e = OutboxEntry.pending("FOO", new byte[0]);
store.save(e);
OutboxPoller poller = new OutboxPoller(store, fakeBus);
poller.pollNow();
assertThat(published).hasSize(1);
assertThat(store.pollPending(10)).isEmpty();
```

### T11: pollNow() increments retries on failure, then marks FAILED after maxRetries
```java
KairoEventBus failingBus = /* always throws */;
OutboxPoller poller = new OutboxPoller(store, failingBus, 3);
OutboxEntry e = OutboxEntry.pending("FOO", new byte[0]);
store.save(e);

poller.pollNow(); // retry 1
poller.pollNow(); // retry 2
poller.pollNow(); // retry 3 → markFailed

assertThat(store.pollPending(10)).isEmpty();
```

### T12: start/stop lifecycle — no entries after stop
```java
OutboxPoller poller = new OutboxPoller(store, fakeBus);
poller.start();
Thread.sleep(250); // let 2 poll cycles fire
poller.stop(); // must not hang
```
