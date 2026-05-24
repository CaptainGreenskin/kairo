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

    public DangerousCommandPolicy() {
        this(DEFAULT_SHELL_TOOLS);
    }

    public DangerousCommandPolicy(Set<String> shellTools) {
        this.shellTools =
                shellTools == null || shellTools.isEmpty()
                        ? DEFAULT_SHELL_TOOLS
                        : Set.copyOf(shellTools);
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

        for (Pattern p : HARDLINE_PATTERNS) {
            if (p.matcher(command).find()) {
                return Mono.just(
                        GuardrailDecision.deny(
                                "Blocked by hardline safety rule: " + p.pattern(), NAME));
            }
        }
        for (Pattern p : DANGEROUS_PATTERNS) {
            if (p.matcher(command).find()) {
                return Mono.just(
                        GuardrailDecision.warn(
                                "Potentially dangerous command detected: " + p.pattern(), NAME));
            }
        }
        return Mono.just(GuardrailDecision.allow(NAME));
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
