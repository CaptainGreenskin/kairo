# Kairo Test Coverage Plan

> Current: 1,615 @Test / 137 files / 0 fail / BUILD SUCCESS
> Target: Cover all critical paths before public release

## Current Coverage by Module

| Module | Prod Files | Test Files | @Test Methods | Untested Sources |
|--------|-----------|------------|---------------|-----------------|
| kairo-api | ~80 | 25 | 233 | Mostly interfaces/types (expected) |
| kairo-core | ~75 | 76 | 934 | **32 files** (see detail below) |
| kairo-tools | 23 | 13 | 155 | **10 tools** |
| kairo-mcp | ~14 | 8 | 93 | 5 files |
| kairo-multi-agent | ~10 | 6 | 100 | 0 (fully covered) |
| kairo-observability | ~6 | 5 | 72 | 1 file |
| kairo-spring-boot-starter | ~4 | 4 | 28 | 2 files |

## P0: Critical Path — Must Have Before Public Release

These tests protect the core execution path. None require API keys.

### P0-A: Compaction Pipeline (2,055 lines untested)

The 6-stage progressive compaction pipeline is Kairo's strongest differentiator.
Currently zero direct tests. This is the single biggest quality gap.

| Test Class | Target | Lines | Key Assertions |
|------------|--------|-------|----------------|
| `SnipCompactionTest` | SnipCompaction (205 lines) | ~60 | Old tool results replaced with "[snipped]", non-tool messages preserved |
| `MicroCompactionTest` | MicroCompaction (193 lines) | ~60 | Tool result content cleared but message structure kept, user/assistant messages untouched |
| `CollapseCompactionTest` | CollapseCompaction (174 lines) | ~55 | Consecutive same-role messages folded into one, mixed roles preserved |
| `PartialCompactionTest` | PartialCompaction (255 lines) | ~70 | Head + tail kept, middle compressed, boundary messages preserved |
| `AutoCompactionTest` | AutoCompaction (226 lines) | ~50 | LLM summary triggered at 95% pressure, fallback when LLM unavailable |
| `CompactionPipelineTest` | CompactionPipeline (303 lines) | ~80 | Strategies fire in threshold order, circuit breaker stops runaway, empty input handled |
| `CompactionTriggerTest` | CompactionTrigger (74 lines) | ~40 | Pressure calculation correct, trigger threshold respected, no trigger when below threshold |
| `HybridThresholdTest` | HybridThreshold (62 lines) | ~30 | Threshold comparison logic, edge cases |

**Total: ~445 test lines, ~40 @Test methods**

Test pattern for each strategy:

```java
@Test
void strategyReducesTokenCount() {
    // Given: messages that exceed the pressure threshold
    List<Msg> messages = buildConversationAtPressure(0.85);
    int tokensBefore = estimateTokens(messages);

    // When: apply compaction
    CompactionResult result = strategy.compact(messages, config);

    // Then: output is smaller
    assertThat(result.tokensSaved()).isGreaterThan(0);
    assertThat(result.compactedMessages()).hasSizeLessThanOrEqualTo(messages.size());
}

@Test
void strategyPreservesUserMessages() {
    // User messages must NEVER be modified (Facts First principle)
    List<Msg> messages = buildConversationWithUserMessages();

    CompactionResult result = strategy.compact(messages, config);

    result.compactedMessages().stream()
        .filter(m -> m.role() == MsgRole.USER)
        .forEach(m -> assertThat(m.text()).isEqualTo(originalText(m)));
}
```

### P0-B: Agent Builder Validation (0 tests for validation logic)

AgentBuilder.build() has multiple validation checks but no dedicated tests.

| Test Class | Target | Key Assertions |
|------------|--------|----------------|
| `AgentBuilderValidationTest` | AgentBuilder parameter checks | NPE when name is null, ISE when modelName missing, IAE when maxIterations ≤ 0, IAE when tokenBudget ≤ 0 |
| `AgentBuilderStreamingTest` | streaming() builder method | streaming(true) produces agent with streaming enabled, streaming(false) (default) produces agent with streaming disabled |

**Total: ~80 test lines, ~8 @Test methods**

### P0-C: System Prompt Builder (471 lines untested)

System prompt construction affects every API call quality.

| Test Class | Target | Key Assertions |
|------------|--------|----------------|
| `SystemPromptBuilderTest` | Section-based prompt building | Sections rendered in order, dynamicBoundary() splits static/dynamic, CacheScope correct per section, empty builder produces empty prompt |
| `SystemPromptResultTest` | Result record | staticPrefix/dynamicSuffix split correct, segments list matches sections |

**Total: ~100 test lines, ~10 @Test methods**

---

## P1: Feature Completeness — v0.2.x Cycle

### P1-A: Untested Tools (10 tools, 1,104 lines total)

| Tool | Lines | Test Difficulty | Key Test |
|------|-------|----------------|----------|
| `EnterPlanModeTool` | 105 | Easy | State transition to PLAN_MODE, system prompt updated |
| `ExitPlanModeTool` | 134 | Easy | State transition back to EXECUTION, write tools re-enabled |
| `ListPlansTool` | 91 | Easy | Lists plan files from .kairo/plans/ |
| `TaskGetTool` | 72 | Easy | Retrieves task by ID from TaskBoard |
| `TeamCreateTool` | 64 | Easy | Creates team in TeamManager |
| `TeamDeleteTool` | 57 | Easy | Removes team, verifies not found after |
| `AskUserTool` | 127 | Medium | Requires mock UserApprovalHandler |
| `MonitorTool` | 139 | Medium | Requires mock Process |
| `AgentSpawnTool` | 144 | Medium | Requires mock AgentFactory |
| `OpenApiHttpTool` | 171 | Medium | Requires mock HTTP server (OkHttp MockWebServer) |

Test pattern:

```java
@Test
void planModeToolTransitionsState() {
    EnterPlanModeTool tool = new EnterPlanModeTool();
    Map<String, Object> input = Map.of();

    ToolResult result = tool.execute(input);

    assertThat(result.isError()).isFalse();
    // Verify agent state changed to PLAN_MODE
}
```

**Total: ~300 test lines, ~25 @Test methods**

### P1-B: Error Recovery (116 lines untested)

| Test Class | Target | Key Assertions |
|------------|--------|----------------|
| `ApiErrorClassifierImplTest` | Error classification | rate_limited from 429, server_error from 500, prompt_too_long from token count, unknown errors handled |
| `ErrorRecoveryIntegrationTest` | Retry/fallback in agent loop | Retry on rate limit, fallback model on server error, fail after max retries |

**Total: ~80 test lines, ~8 @Test methods**

### P1-C: Session & Plan Persistence

| Test Class | Target | Key Assertions |
|------------|--------|----------------|
| `SessionResumptionTest` | Session recovery (96 lines) | Serialize → deserialize round-trip, partial session recovery, missing session file handled |
| `PlanFileManagerTest` | Plan file CRUD (226 lines) | Create/read/list/delete plans, plan file format correct, concurrent access safe |

**Total: ~100 test lines, ~10 @Test methods**

### P1-D: Spring Boot Negative Scenarios

Already partially covered (NegativeAutoConfigTest exists with 7 tests).
Add:

| Test | Key Assertion |
|-------|---------------|
| Custom ToolRegistry bean overrides default | No ClassCastException |
| Custom MemoryStore bean overrides default | File store not created when custom provided |
| Missing API key + openai provider | Clear error message |
| Empty tool categories | Agent created with empty tool registry |

**Total: ~40 test lines, ~4 @Test methods**

---

## P2: Robustness & Edge Cases — v0.3.0 Cycle

### P2-A: Concurrency Safety

| Test Class | Target | Key Assertions |
|------------|--------|----------------|
| `FileMemoryStoreConcurrencyTest` | Concurrent writes/reads | 20 threads writing simultaneously → all entries present, no data corruption |
| `InMemoryStoreConcurrencyTest` | Concurrent access | Thread-safe under parallel save/search/delete |
| `DefaultContextManagerConcurrencyTest` | Message list mutations | Concurrent addMessage + compaction → no CME, token count consistent |

**Total: ~120 test lines, ~6 @Test methods**

### P2-B: Memory Middleware SPI (new feature tests)

| Test Class | Target | Key Assertions |
|------------|--------|----------------|
| `MemoryMiddlewareChainTest` | Middleware execution order | Middlewares fire in order() sequence, null from onSave discards entry |
| `DedupMiddlewareTest` | Example middleware | Duplicate entries filtered, unique entries pass through |
| `MemoryScopeTest` | Hierarchical paths | child() creates sub-path, isParentOf works, session/project/user factories correct |

**Total: ~100 test lines, ~10 @Test methods**

### P2-C: Spring Boot Demo MockMvc

| Test Class | Target | Key Assertions |
|------------|--------|----------------|
| `ChatControllerMvcTest` | POST /chat | 200 with valid input, 400 with empty input |
| `StructuredOutputControllerMvcTest` | POST /extract | Returns typed JSON, handles provider failure |
| `StreamingChatControllerMvcTest` | GET /stream | Returns SSE content type, multiple chunks |
| `PermissionGuardControllerMvcTest` | POST /guarded | Blocks without permission, allows with permission |

**Total: ~150 test lines, ~12 @Test methods**

### P2-D: Skill System

| Test Class | Target | Key Assertions |
|------------|--------|----------------|
| `SkillLoaderTest` | Skill loading (202 lines) | Load from classpath, parse YAML frontmatter, invalid format handled gracefully |
| `SkillToolManagerTest` | Tool-skill wiring (135 lines) | Skills registered as tools, TriggerGuard filters correctly |

**Total: ~80 test lines, ~8 @Test methods**

---

## P3: Integration / E2E — Requires API Key

Tagged with `@Tag("integration")`, excluded from default CI.

| Test Class | What | API Key |
|------------|------|---------|
| `AnthropicProviderIntegrationTest` | Real Claude API call + streaming | ANTHROPIC_API_KEY |
| `OpenAIProviderIntegrationTest` | Real Qwen/GLM call | QWEN_API_KEY or GLM_API_KEY |
| `CompactionWithRealLLMTest` | AutoCompaction with real summary | Any key |
| `SpringBootE2ETest` | Full REST round-trip with real LLM | Any key |

**Run: `mvn test -Pintegration-tests`**

---

## Execution Timeline

```
Phase 1 (Before public release):
  P0-A: Compaction pipeline tests          ~445 lines / ~40 tests
  P0-B: AgentBuilder validation tests       ~80 lines / ~8 tests
  P0-C: SystemPromptBuilder tests          ~100 lines / ~10 tests
  ─────────────────────────────────────────────────────────
  Total:                                    ~625 lines / ~58 tests

Phase 2 (v0.2.x):
  P1-A: 10 untested tools                  ~300 lines / ~25 tests
  P1-B: Error recovery                     ~80 lines / ~8 tests
  P1-C: Session & Plan persistence         ~100 lines / ~10 tests
  P1-D: Spring Boot negative scenarios      ~40 lines / ~4 tests
  ─────────────────────────────────────────────────────────
  Total:                                    ~520 lines / ~47 tests

Phase 3 (v0.3.0):
  P2-A: Concurrency safety                 ~120 lines / ~6 tests
  P2-B: Memory Middleware SPI              ~100 lines / ~10 tests
  P2-C: Spring Boot Demo MockMvc           ~150 lines / ~12 tests
  P2-D: Skill system                       ~80 lines / ~8 tests
  ─────────────────────────────────────────────────────────
  Total:                                    ~450 lines / ~36 tests

Grand total: ~1,595 new test lines / ~141 new @Test methods
Projected final: ~1,756 @Test methods / ~170+ test files
```

## Principles

1. **No API key needed for P0-P2** — all tests use mocks or fixed inputs
2. **Facts First testing** — verify that compaction NEVER modifies user messages
3. **One assertion per behavior** — each test verifies one specific behavior
4. **Test method naming** — `methodName_state_result` (e.g., `snipCompaction_atHighPressure_removesOldToolResults`)
5. **Mock pattern** — `mock.module()` + `await import()` in same test file (per CLAUDE.md convention)
6. **Compaction test invariant** — every strategy test must verify:
   - Output tokens < input tokens (or equal when no compression needed)
   - User messages preserved verbatim
   - Agent can continue conversation after compaction
