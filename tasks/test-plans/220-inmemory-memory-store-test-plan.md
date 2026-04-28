# Test Plan: Task 220 — InMemoryMemoryStore + MemoryRelevanceScorer

## Setup

```java
InMemoryMemoryStore store = new InMemoryMemoryStore();
```

---

## MemoryRelevanceScorer

### T1: termOverlap = 1.0 when all query terms appear in content
```java
MemoryEntry e = MemoryEntry.builder().id("1").content("the quick brown fox").build();
double overlap = MemoryRelevanceScorer.computeTermOverlap(e, "quick fox");
assertThat(overlap).isEqualTo(1.0);
```

### T2: termOverlap = 0.5 when half query terms match
```java
MemoryEntry e = MemoryEntry.builder().id("1").content("quick cat").build();
double overlap = MemoryRelevanceScorer.computeTermOverlap(e, "quick fox");
assertThat(overlap).isEqualTo(0.5);
```

### T3: termOverlap = 0.0 when no terms match
```java
MemoryEntry e = MemoryEntry.builder().id("1").content("nothing here").build();
double overlap = MemoryRelevanceScorer.computeTermOverlap(e, "quick fox");
assertThat(overlap).isEqualTo(0.0);
```

### T4: recency = 1.0 for just-created entry
```java
MemoryEntry e = MemoryEntry.builder().id("1").timestamp(Instant.now()).build();
double recency = MemoryRelevanceScorer.computeRecency(e, Instant.now());
assertThat(recency).isCloseTo(1.0, offset(0.01));
```

### T5: recency ≈ 0.5 for 7-day-old entry (half-life)
```java
MemoryEntry e = MemoryEntry.builder().id("1").timestamp(Instant.now().minus(7, DAYS)).build();
double recency = MemoryRelevanceScorer.computeRecency(e, Instant.now());
assertThat(recency).isCloseTo(0.5, offset(0.01));
```

### T6: recency = 0.5 when timestamp is null
```java
MemoryEntry e = MemoryEntry.builder().id("1").timestamp(null).build();
double recency = MemoryRelevanceScorer.computeRecency(e, Instant.now());
assertThat(recency).isEqualTo(0.5);
```

### T7: score formula = 0.7 * termOverlap + 0.3 * recency
```java
// Perfect term match, fresh entry → score close to 1.0
MemoryEntry e = MemoryEntry.builder().id("1").content("fox").timestamp(Instant.now()).build();
double score = MemoryRelevanceScorer.score(e, "fox", Instant.now());
assertThat(score).isGreaterThan(0.95);
```

---

## InMemoryMemoryStore

### T8: save and get round-trip
```java
MemoryEntry e = MemoryEntry.builder().id("m1").content("hello").scope(AGENT).build();
store.save(e).block();
assertThat(store.get("m1").block()).isEqualTo(e);
```

### T9: save overwrites existing entry with same id
```java
store.save(entry("m1", "v1")).block();
store.save(entry("m1", "v2")).block();
assertThat(store.get("m1").block().content()).isEqualTo("v2");
assertThat(store.size()).isEqualTo(1);
```

### T10: search returns results sorted by relevance (most relevant first)
```java
store.save(entry("a", "the quick brown fox", AGENT)).block();
store.save(entry("b", "unrelated content", AGENT)).block();
List<MemoryEntry> results = store.search("quick fox", AGENT).collectList().block();
assertThat(results.get(0).id()).isEqualTo("a");
```

### T11: search filters by scope
```java
store.save(entry("a", "shared text", GLOBAL)).block();
store.save(entry("b", "shared text", AGENT)).block();
List<MemoryEntry> agentResults = store.search("shared", AGENT).collectList().block();
assertThat(agentResults).extracting(MemoryEntry::id).containsOnly("b");
```

### T12: delete removes entry
```java
store.save(entry("m1", "delete me")).block();
store.delete("m1").block();
assertThat(store.get("m1").block()).isNull();
```

### T13: delete non-existent id is no-op
```java
assertThatCode(() -> store.delete("does-not-exist").block()).doesNotThrowAnyException();
```

### T14: list returns entries for scope in descending timestamp order
```java
store.save(entry("old", "text", AGENT, Instant.now().minus(10, DAYS))).block();
store.save(entry("new", "text", AGENT, Instant.now())).block();
List<MemoryEntry> list = store.list(AGENT).collectList().block();
assertThat(list.get(0).id()).isEqualTo("new");
```

### T15: clearAgent removes only that agent's entries
```java
store.save(entryForAgent("m1", "agent-1")).block();
store.save(entryForAgent("m2", "agent-2")).block();
store.clearAgent("agent-1");
assertThat(store.get("m1").block()).isNull();
assertThat(store.get("m2").block()).isNotNull();
```
