/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.core.guardrail.policy;

import io.kairo.api.guardrail.GuardrailContext;
import io.kairo.api.guardrail.GuardrailDecision;
import io.kairo.api.guardrail.GuardrailPayload;
import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.GuardrailPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import reactor.core.publisher.Mono;

/**
 * PRE_TOOL guardrail that blocks (HARDLINE) or warns (DANGEROUS) on shell-style commands the model
 * issues to a bash / shell / code-execute tool. Promoted from kairo-assistant's
 * DangerousCommandPolicy in M-F5a so any kairo-based agent (kairo-code, kairo-assistant, third
 * party) has the same baseline protection without re-implementing the regex catalog.
 *
 * <p>Default shell tool names cover the two conventions in the Kairo ecosystem today:
 *
 * <ul>
 *   <li>{@code "bash"} — kairo-tools convention (kairo-code uses this)
 *   <li>{@code "shell"}, {@code "code_execute"} — kairo-assistant convention
 * </ul>
 *
 * Override with {@link #DangerousCommandPolicy(Set)} when adding a new tool naming scheme.
 */
public class DangerousCommandPolicy implements GuardrailPolicy {

    private static final String NAME = "DangerousCommandPolicy";

    /** Tool names this policy guards by default. */
    public static final Set<String> DEFAULT_SHELL_TOOLS =
            Set.of("bash", "shell", "code_execute", "execute", "run");

    private static final List<Pattern> HARDLINE_PATTERNS =
            List.of(
                    Pattern.compile("rm\\s+-[\\w]*r[\\w]*f[\\w]*\\s+/\\s*$"),
                    Pattern.compile("rm\\s+-[\\w]*f[\\w]*r[\\w]*\\s+/\\s*$"),
                    Pattern.compile("mkfs\\."),
                    Pattern.compile("dd\\s+.*of=/dev/[sh]d"),
                    Pattern.compile(Pattern.quote(":(){ :|:& };:")),
                    Pattern.compile("shutdown\\s"),
                    Pattern.compile("reboot\\b"),
                    Pattern.compile("init\\s+0"),
                    Pattern.compile("halt\\b"),
                    Pattern.compile("\\bchmod\\s+-R\\s+777\\s+/\\s*$"),
                    Pattern.compile("\\bchown\\s+-R\\s+.*\\s+/\\s*$"));

    private static final List<Pattern> DANGEROUS_PATTERNS =
            List.of(
                    Pattern.compile("rm\\s+-[\\w]*r"),
                    Pattern.compile("\\|\\s*sudo\\b"),
                    Pattern.compile("sudo\\s+-S\\b"),
                    Pattern.compile("curl\\s+.*\\|\\s*(sh|bash|zsh)"),
                    Pattern.compile("wget\\s+.*\\|\\s*(sh|bash|zsh)"),
                    Pattern.compile(">(\\s*/dev/(sd|hd|nvme))"),
                    Pattern.compile("\\bkill\\s+-9\\s+-1\\b"),
                    Pattern.compile("\\bkillall\\b"),
                    Pattern.compile("\\bpkill\\s+-9\\b"),
                    Pattern.compile("\\bgit\\s+push\\s+.*--force\\b"),
                    Pattern.compile("\\bgit\\s+reset\\s+--hard\\b"),
                    Pattern.compile("\\bdrop\\s+database\\b", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("\\bdrop\\s+table\\b", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("\\btruncate\\s+table\\b", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("\\bformat\\s+[a-zA-Z]:\\b"),
                    Pattern.compile("\\bnc\\s+-[\\w]*l"),
                    Pattern.compile("\\biptables\\s+-F\\b"),
                    Pattern.compile("\\bchmod\\s+777\\b"),
                    Pattern.compile("\\bexport\\s+.*PASSWORD"),
                    Pattern.compile("\\bcrontab\\s+-r\\b"));

    private static final List<String> COMMAND_ARG_KEYS =
            List.of("command", "code", "script", "cmd");

    private final Set<String> shellTools;

    /**
     * Optional fallback consulted only when {@link BashCommandClassifier} returns {@link
     * BashCommandClassifier.Category#UNKNOWN}. {@code null} keeps the legacy heuristic-only
     * behaviour. See {@link LlmBashClassifier} for the LLM fallback contract.
     */
    private final LlmBashClassifier llmFallback;

    public DangerousCommandPolicy() {
        this(DEFAULT_SHELL_TOOLS, null);
    }

    public DangerousCommandPolicy(Set<String> shellTools) {
        this(shellTools, null);
    }

    /**
     * Construct with a custom shell-tool set and an optional LLM fallback for commands the
     * heuristic classifier cannot identify. The LLM is only consulted when the heuristic returns
     * {@link BashCommandClassifier.Category#UNKNOWN}; for known categories the policy stays a pure
     * regex pass, identical to the no-arg constructor.
     *
     * @param shellTools tool names this policy guards; null/empty → {@link #DEFAULT_SHELL_TOOLS}
     * @param llmFallback optional LLM classifier; {@code null} disables LLM fallback
     */
    public DangerousCommandPolicy(Set<String> shellTools, LlmBashClassifier llmFallback) {
        this.shellTools =
                shellTools == null || shellTools.isEmpty()
                        ? DEFAULT_SHELL_TOOLS
                        : Set.copyOf(shellTools);
        this.llmFallback = llmFallback;
    }

    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (context.phase() != GuardrailPhase.PRE_TOOL) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }
        if (!(context.payload() instanceof GuardrailPayload.ToolInput toolInput)) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }
        if (!shellTools.contains(toolInput.toolName())) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }

        String command = extractCommand(toolInput.args());
        if (command == null || command.isBlank()) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }

        // Categorise first — informs the decision and is surfaced in the reason for
        // audit log / UI consumers via the [CATEGORY] prefix convention.
        BashCommandClassifier.Category heuristic = BashCommandClassifier.classify(command);

        // When the heuristic is confident, decide synchronously. The LLM fallback is consulted
        // ONLY for the UNKNOWN residual, so the happy path is identical to the no-arg ctor.
        if (heuristic != BashCommandClassifier.Category.UNKNOWN || llmFallback == null) {
            return Mono.just(decide(command, heuristic));
        }

        // Heuristic UNKNOWN + LLM fallback configured: defer to the model, then route the
        // resulting category through the same decide() ladder. classify() never errors —
        // failures degrade to UNKNOWN which falls through to ALLOW (preserves today's
        // semantics during model outages; failure is logged + audited inside the classifier).
        return llmFallback.classify(command).map(refined -> decide(command, refined));
    }

    /**
     * Resolve a {@link GuardrailDecision} for a command + its classifier category. Pure regex pass,
     * no I/O. Shared between the heuristic-only path and the LLM-fallback path.
     */
    private GuardrailDecision decide(String command, BashCommandClassifier.Category category) {
        // Hardline regex catalog still wins (deny). It existed before the classifier and
        // has been tuned against real attack patterns; we keep it as the source of truth.
        for (Pattern p : HARDLINE_PATTERNS) {
            if (p.matcher(command).find()) {
                return GuardrailDecision.deny(
                        "[" + category + "] Blocked by hardline safety rule: " + p.pattern(), NAME);
            }
        }

        // DESTRUCTIVE category catches commands the legacy regex missed (DROP TABLE,
        // TRUNCATE TABLE, DELETE FROM ... no WHERE, fork bomb variants). Deny upgrade
        // from the legacy "warn" decision these would have produced.
        if (category == BashCommandClassifier.Category.DESTRUCTIVE) {
            return GuardrailDecision.deny(
                    "[DESTRUCTIVE] Command classified as destructive by BashCommandClassifier;"
                            + " refusing.",
                    NAME);
        }

        // Dangerous regex catalog → warn. Tag with the classifier's verdict so audit
        // log entries record the bucket (network / exec / write etc.).
        for (Pattern p : DANGEROUS_PATTERNS) {
            if (p.matcher(command).find()) {
                return GuardrailDecision.warn(
                        "[" + category + "] Potentially dangerous command detected: " + p.pattern(),
                        NAME);
            }
        }

        // EXEC classifier verdict (curl|sh etc.) catches the gray zone the dangerous
        // catalog might miss with different quoting / spacing. Warn so the user can
        // confirm; don't deny outright.
        if (category == BashCommandClassifier.Category.EXEC) {
            return GuardrailDecision.warn(
                    "[EXEC] Command classified as untrusted-input execution by"
                            + " BashCommandClassifier; confirm before running.",
                    NAME);
        }

        return GuardrailDecision.allow(NAME);
    }

    @Override
    public int order() {
        return -90;
    }

    @Override
    public String name() {
        return NAME;
    }

    private String extractCommand(Map<String, Object> args) {
        if (args == null) return null;
        for (String key : COMMAND_ARG_KEYS) {
            Object val = args.get(key);
            if (val instanceof String s) return s;
        }
        return null;
    }
}
