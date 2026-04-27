# Test Plan: 224 — AgentHealthEndpoint

## Scope

`AgentHealthRegistry`, `AgentHealthInfo`, `KairoAgentsEndpoint`, `DefaultReActAgent` integration

## Test Cases

### TC-01: Registry.snapshot() returns empty list when no agents registered
- Call `AgentHealthRegistry.global().snapshot()`
- Assert: returns empty list

### TC-02: Registry reflects registered agent
- Register a supplier that returns a fixed AgentHealthInfo
- Call `snapshot()`
- Assert: list contains the registered info

### TC-03: deregister removes agent from snapshot
- Register, then deregister by id
- Call `snapshot()`
- Assert: empty list

### TC-04: snapshot() reflects live state via supplier
- Register supplier returning `new AgentHealthInfo("a1", "agent", RUNNING, 5, now)`
- Update mutable state, call snapshot again
- Assert: snapshot always reads from supplier at call time

### TC-05: Multiple agents in registry
- Register 3 suppliers
- Assert: `snapshot()` returns 3 entries

### TC-06: Supplier exception is silently swallowed
- Register a supplier that throws RuntimeException
- Call snapshot()
- Assert: empty list (no exception propagated)

### TC-07: AgentHealthInfo record accessors
- Construct `new AgentHealthInfo("id", "name", IDLE, 0, Instant.now())`
- Assert each accessor returns correct value

### TC-08: DefaultReActAgent registers itself on construction
- Create a DefaultReActAgent (in test, use minimal config + mock toolExecutor)
- Call `AgentHealthRegistry.global().snapshot()`
- Assert: at least one entry with matching `agentId` and `name`

### TC-09: DefaultReActAgent deregisters on interrupt()
- Create agent, verify it appears in registry
- Call `agent.interrupt()`
- Assert: agent no longer appears in snapshot

### TC-10: KairoAgentsEndpoint.agents() delegates to registry
- Inject a mock/real registry into endpoint
- Verify returned list matches registry.snapshot()

### TC-11: Concurrent registration is thread-safe
- 20 threads each register a unique agent simultaneously
- Call snapshot() after all threads finish
- Assert: snapshot contains all 20 entries

### TC-12: iterationCount increments are visible in snapshot
- Register supplier that reads `AtomicInteger.get()`
- Increment the AtomicInteger 10 times
- Call snapshot()
- Assert: iterationCount == 10
