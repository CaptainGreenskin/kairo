# ADR-024 — PII Redaction as a Guardrail Policy (no new SPI)

## Status

Accepted — adopted in v1.0.0 (Wave 3C, 2026-04-24). Introduces module
`kairo-security-pii` containing the stock `PiiRedactionPolicy` plus its catalogue of regex
patterns. **No new SPI** is added: PII redaction is wired as an implementation of the
existing `io.kairo.api.guardrail.GuardrailPolicy` contract.

## Context

The v1.0 GA program scope (`valiant-honking-coral` plan, Wave 3C) called out an enterprise
security trio that needs to land before GA:

1. PII filtering across model output and tool output
2. Audit log framework (`JdbcAuditEventSink`)
3. Compliance report schema

The original plan text proposed a fresh `PiiRedactor` SPI with a default regex-based
implementation. A literal reading of that plan would have introduced a new top-level surface
area inside `kairo-api` whose lifecycle would then need to be governed under ADR-023's
`@Stable` policy.

Three things forced a re-evaluation:

1. **SPI design philosophy**: the project's standing guidance (incubation-stage, see
   feedback memory `feedback_spi_design_philosophy.md`) says "不新增抽象，用现有 SPI 覆盖
   需求" — do not introduce new abstractions when an existing SPI already covers the need.
2. **Existing surface** already fits: `GuardrailPolicy` evaluates at
   `POST_MODEL` and `POST_TOOL` boundaries, returns a `GuardrailDecision`, and includes a
   `MODIFY` action that replaces the payload in-flight. That is precisely the shape PII
   redaction needs — inspect text, replace matches, hand the new payload back to the chain.
3. **Composition**: hosting apps already wire guardrail policies through
   `GuardrailChain`. If PII redaction is "just another policy," users get ordering, denial
   semantics, observability, and chain-level testing for free. A separate SPI would
   duplicate every one of those concerns.

## Decision

PII redaction ships as **module `kairo-security-pii`**, exporting a single concrete policy
plus its config + pattern catalogue. The module depends only on `kairo-api` + `reactor-core`
+ `slf4j-api` and adds **zero** types to `io.kairo.api`.

### Module surface

| Type | Kind | Purpose |
|------|------|---------|
| `PiiRedactionPolicy` | `final class implements GuardrailPolicy` | Stock policy, name `pii-redaction`, default order 100 |
| `PiiRedactionConfig` | `record` | `Map<Pattern,String> patterns + Set<GuardrailPhase> phases + int order`, with `defaults()`, `of(PiiPattern...)`, `withPhases(...)`, `withOrder(int)` |
| `PiiPattern` | `enum` | Catalogue of 6 shipped regex/replacement pairs: `EMAIL`, `PHONE_US`, `CREDIT_CARD`, `SSN_US`, `API_KEY`, `JWT` |

`RedactionResult` (text + matchCount) is an `internal` package-private record — not part of
the surface.

### Behavior

- **Phase gating**: policy returns `ALLOW("phase skipped")` when the context phase is not in
  `config.phases()`. Defaults to `POST_MODEL + POST_TOOL`. A `toolOutputOnly()` factory pins
  to `POST_TOOL` for hosting apps that trust their own model output.
- **`ToolOutput`**: rebuilds `ToolResult` preserving `toolUseId` / `isError` / `metadata`,
  replacing only `content`. Returns `MODIFY` when matches found, `ALLOW("no PII detected")`
  otherwise.
- **`ModelOutput`**: walks `response.contents()`, redacts only `TextContent` and
  `ThinkingContent` (other `Content` variants pass through unchanged), then rebuilds
  `ModelResponse` preserving `id` / `usage` / `stopReason` / `model`.
- **Other payloads** (`ModelInput`, `ToolInput`): always `ALLOW("payload not redactable")`
  — this policy intentionally only redacts model/tool **output**, on the principle that
  user input is the authoritative source of intent and silently rewriting it is the wrong
  default.

### What we deliberately did NOT do

- ❌ Add a new `PiiRedactor` interface to `kairo-api`. (Would have grown ADR-023's
  `@Stable` scope by one type with no behavioral payoff.)
- ❌ Wire the policy into `ToolResultSanitizer` or `ExceptionMapper` directly. Both already
  collaborate with `GuardrailChain`; threading a separate redactor through them would
  fork the policy story.
- ❌ Add a Spring Boot starter for PII redaction. Hosting apps register the policy as a
  `@Bean` of `GuardrailPolicy`; the existing `kairo-spring-boot-starter-core` chain
  bootstrap picks it up. Adding a starter would imply per-policy auto-config, which is the
  wrong shape — `GuardrailChain` already auto-discovers all `GuardrailPolicy` beans.
- ❌ Make the regex patterns configurable through Spring properties. The catalogue ships
  as Java `enum` constants and the `PiiRedactionConfig` record is the configuration
  surface. Hosting apps that need custom patterns build their own `Map<Pattern,String>`.
  String-typed property catalogues invite injection bugs and broken regex at runtime.

### Stability annotation

- `PiiRedactionPolicy` is **not** annotated `@Stable` or `@Experimental`. Per ADR-023,
  these annotations apply to types under `io.kairo.api.*`. This module ships under
  `io.kairo.security.pii.*` — concrete implementations are not part of the SPI freeze
  surface, and hosting apps can substitute their own `GuardrailPolicy` if they need
  different redaction logic.
- The catalogue (`PiiPattern`) deliberately errs on over-matching. False positives replace
  a non-sensitive string with a placeholder; false negatives leak PII to an untrusted
  observer. The placeholder strings (`<redacted:email>` etc.) are `String` constants on the
  enum and are part of the module's public behavior.

## Consequences

### Positive

- **Zero new SPI surface**. ADR-023's `@Stable` count stays at 119 types.
- **Composability**: PII redaction layers cleanly with other policies (deny-list,
  approval-gate, cost-cap) and inherits all `GuardrailChain` instrumentation
  (`KairoEventBus` publishes a `DOMAIN_SECURITY` event on every `MODIFY`).
- **Testability**: 15 unit tests in `PiiRedactionPolicyTest` cover each pattern + phase
  skip + ToolOutput round-trip + ModelOutput TextContent+ThinkingContent round-trip +
  config composition + `toolOutputOnly()` factory + custom pattern subset. No mock harness
  needed — the policy is a pure function from `GuardrailContext` to `Mono<GuardrailDecision>`.

### Negative / accepted trade-offs

- The policy is a **post-hoc** safeguard. PII that flows into the prompt before the model
  call is not protected by this policy (PRE_MODEL is intentionally excluded — see "What we
  deliberately did NOT do"). Hosting apps that need pre-model redaction must register a
  separate `GuardrailPolicy` for the `PRE_MODEL` phase.
- Regex-based redaction is fundamentally lossy: false positives can mangle non-sensitive
  text that happens to look PII-shaped. Documented in the `PiiPattern` Javadoc.
- The module does not ship a starter. Hosting apps wire one line:
  ```java
  @Bean public GuardrailPolicy piiRedaction() { return PiiRedactionPolicy.stock(); }
  ```
  This is a feature, not a bug — explicit registration documents intent at the call site.

## References

- `kairo-security-pii/pom.xml` — module declaration
- `kairo-security-pii/src/main/java/io/kairo/security/pii/PiiRedactionPolicy.java` — implementation
- `kairo-security-pii/src/test/java/io/kairo/security/pii/PiiRedactionPolicyTest.java` — 15 unit tests
- ADR-007 — Guardrail SPI design (the host SPI this policy implements)
- ADR-023 — SPI stability policy (the gate that motivated avoiding a new SPI)
- Memory: `feedback_spi_design_philosophy.md` — "不新增抽象，用现有 SPI 覆盖需求"
