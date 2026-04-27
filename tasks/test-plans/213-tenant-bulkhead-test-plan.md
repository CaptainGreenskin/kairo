# Test Plan: Task 213 — TenantBulkheadRegistry + BulkheadMiddleware

## Setup

```java
TenantBulkheadConfig config = new TenantBulkheadConfig(
    TierBulkheadLimits.FREE, Map.of());  // FREE: 5 concurrent, 2 rps, burst 5
TenantBulkheadRegistry registry = new TenantBulkheadRegistry(config);
ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
TenantContext acme = new TenantContext("acme", "u1", Map.of());
TenantContext pro = new TenantContext("pro-tenant", "u2", Map.of("plan-tier", "pro"));
```

---

## TierBulkheadLimits

### T1: FREE preset
```java
assertThat(TierBulkheadLimits.FREE.maxConcurrent()).isEqualTo(5);
assertThat(TierBulkheadLimits.FREE.ratePerSecond()).isEqualTo(2.0);
assertThat(TierBulkheadLimits.FREE.burstCapacity()).isEqualTo(5);
```

### T2: forTierName resolves all tiers
```java
assertThat(TierBulkheadLimits.forTierName("pro")).isEqualTo(TierBulkheadLimits.PRO);
assertThat(TierBulkheadLimits.forTierName("enterprise")).isEqualTo(TierBulkheadLimits.ENTERPRISE);
assertThat(TierBulkheadLimits.forTierName("unlimited")).isEqualTo(TierBulkheadLimits.UNLIMITED);
assertThat(TierBulkheadLimits.forTierName(null)).isEqualTo(TierBulkheadLimits.FREE);
assertThat(TierBulkheadLimits.forTierName("unknown")).isEqualTo(TierBulkheadLimits.FREE);
```

---

## TenantBulkheadConfig

### T3: Per-tenant override wins over plan-tier
```java
TenantContext t = new TenantContext("vip", "u", Map.of("plan-tier", "free"));
TenantBulkheadConfig cfg = new TenantBulkheadConfig(
    TierBulkheadLimits.FREE,
    Map.of("vip", TierBulkheadLimits.ENTERPRISE));
assertThat(cfg.limitsFor(t)).isEqualTo(TierBulkheadLimits.ENTERPRISE);
```

### T4: plan-tier attribute used when no override
```java
TenantContext t = new TenantContext("x", "u", Map.of("plan-tier", "pro"));
TenantBulkheadConfig cfg = new TenantBulkheadConfig(TierBulkheadLimits.FREE, Map.of());
assertThat(cfg.limitsFor(t)).isEqualTo(TierBulkheadLimits.PRO);
```

### T5: Falls back to defaultLimits
```java
TenantContext t = new TenantContext("anon", "u", Map.of());
TenantBulkheadConfig cfg = new TenantBulkheadConfig(TierBulkheadLimits.PRO, Map.of());
assertThat(cfg.limitsFor(t)).isEqualTo(TierBulkheadLimits.PRO);
```

---

## TenantBulkhead

### T6: Concurrency limit enforced — extra acquire fails
```java
TenantBulkhead b = new TenantBulkhead("t", new TierBulkheadLimits(2, 100.0, 100));
assertThat(b.tryAcquireConcurrency()).isTrue();
assertThat(b.tryAcquireConcurrency()).isTrue();
assertThat(b.tryAcquireConcurrency()).isFalse();  // slot 3 should fail
b.releaseConcurrency();
assertThat(b.tryAcquireConcurrency()).isTrue();   // after release, slot freed
```

### T7: Rate limit enforced — burst consumed, then rejected
```java
TenantBulkhead b = new TenantBulkhead("t", new TierBulkheadLimits(10, 2.0, 3));
// consume 3 tokens (burst)
assertThat(b.tryAcquireRate()).isTrue();
assertThat(b.tryAcquireRate()).isTrue();
assertThat(b.tryAcquireRate()).isTrue();
assertThat(b.tryAcquireRate()).isFalse();  // burst exhausted
```

### T8: UNLIMITED limits — never rejects
```java
TenantBulkhead b = new TenantBulkhead("t", TierBulkheadLimits.UNLIMITED);
for (int i = 0; i < 1000; i++) {
    assertThat(b.tryAcquireRate()).isTrue();
    assertThat(b.tryAcquireConcurrency()).isTrue();
}
assertThat(b.availableConcurrency()).isEqualTo(Integer.MAX_VALUE);
```

### T9: execute() — success path, concurrency released
```java
TenantBulkhead b = new TenantBulkhead("t", new TierBulkheadLimits(1, 100.0, 100));
String result = b.execute(() -> "ok");
assertThat(result).isEqualTo("ok");
assertThat(b.availableConcurrency()).isEqualTo(1);  // slot released
```

### T10: execute() — BulkheadRejectedException on concurrency exceeded
```java
TenantBulkhead b = new TenantBulkhead("t", new TierBulkheadLimits(1, 100.0, 100));
b.tryAcquireConcurrency();  // take only slot
assertThatThrownBy(() -> b.execute(() -> "x"))
    .isInstanceOf(BulkheadRejectedException.class)
    .satisfies(e -> assertThat(((BulkheadRejectedException)e).tenantId()).isEqualTo("t"));
```

---

## TenantBulkheadRegistry

### T11: Same tenant → same bulkhead instance (lazy creation, cached)
```java
TenantBulkhead b1 = registry.get(acme);
TenantBulkhead b2 = registry.get(acme);
assertThat(b1).isSameAs(b2);
assertThat(registry.size()).isEqualTo(1);
```

### T12: Different tenants → different bulkhead instances
```java
TenantBulkhead b1 = registry.get(acme);
TenantBulkhead b2 = registry.get(pro);
assertThat(b1).isNotSameAs(b2);
assertThat(registry.size()).isEqualTo(2);
```

### T13: clear() removes cached bulkheads
```java
registry.get(acme);
registry.clear();
assertThat(registry.size()).isEqualTo(0);
```

---

## BulkheadMiddleware

### T14: Passes through when under limits
```java
BulkheadMiddleware mw = new BulkheadMiddleware(registry, holder);
MiddlewareContext ctx = /* minimal ctx with no "tenant" attr */;
MiddlewareChain chain = c -> Mono.just(c);
StepVerifier.create(mw.handle(ctx, chain))
    .expectNextCount(1)
    .verifyComplete();
```

### T15: Resolves tenant from ctx "tenant" attribute first
```java
// ctx has attr "tenant" → acme
// even though holder.current() would return SINGLE
// bulkhead for "acme" should be acquired, not "single"
```

### T16: Rate reject → MiddlewareRejectException
```java
// Exhaust rate tokens by repeated tryAcquireRate() calls on the registry's bulkhead
// Then verify mw.handle() returns Mono.error(MiddlewareRejectException)
// message contains "Rate limit exceeded"
```

### T17: Concurrency reject → MiddlewareRejectException  
```java
// Acquire all concurrency slots manually
// Then verify mw.handle() returns Mono.error(MiddlewareRejectException)
// message contains "Concurrency limit exceeded"
```

### T18: Concurrency slot released on cancellation (doFinally)
```java
// Acquire slot via mw.handle(), then cancel the subscription mid-flight
// Verify availableConcurrency() is restored
```
