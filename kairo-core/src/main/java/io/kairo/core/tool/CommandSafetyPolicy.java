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
package io.kairo.core.tool;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable command safety policy that classifies shell commands into catastrophic (unconditionally
 * blocked) and dangerous (requiring approval) tiers.
 *
 * <p>Extracted from {@code BashTool} to allow reuse across any tool that spawns shell processes.
 *
 * <p>Behaviour can be entirely disabled by setting the environment variable {@code
 * KAIRO_COMMAND_SAFETY_DISABLED=true}.
 *
 * @since 1.3.0
 */
public final class CommandSafetyPolicy {

    private static final Logger log = LoggerFactory.getLogger(CommandSafetyPolicy.class);

    // ── Environment toggle ──────────────────────────────────────────────────────

    private static final boolean DISABLED =
            "true".equalsIgnoreCase(System.getenv("KAIRO_COMMAND_SAFETY_DISABLED"));

    // ── Tier 1: Catastrophic patterns (unconditionally blocked) ─────────────────

    /** Patterns matching commands that would cause irreversible, catastrophic damage. */
    private static final List<Pattern> CATASTROPHIC_PATTERNS =
            List.of(
                    Pattern.compile("\\brm\\s+(-[^\\s]*\\s+)*/\\s*$"),
                    Pattern.compile("\\brm\\s+(-[^\\s]*\\s+)*/\\*"),
                    Pattern.compile("\\brm\\s+(-[^\\s]*\\s+)*~/?(\\s*$|\\*)"),
                    Pattern.compile("\\brm\\s+(-[^\\s]*\\s+)*\\$HOME"),
                    Pattern.compile("\\brm\\s+(-[^\\s]*\\s+)*\\.git/?\\s*$"),
                    Pattern.compile("\\bmkfs\\b"),
                    Pattern.compile("\\bwipefs\\b"),
                    Pattern.compile("\\bshred\\s+/dev/"),
                    Pattern.compile(":\\(\\)\\s*\\{.*\\|.*&\\s*\\}\\s*;\\s*:"),
                    Pattern.compile(">\\s*/dev/(sd|hd|nvme|vd)[a-z]"),
                    Pattern.compile("\\bdd\\s+.*of\\s*=\\s*/dev/"),
                    Pattern.compile("\\bgit\\s+update-ref\\s+-d\\s+HEAD"));

    /** Prefixes that indicate privilege escalation and are unconditionally blocked. */
    private static final List<String> BLOCKED_PREFIXES = List.of("sudo ", "su ", "doas ");

    // ── Tier 2: Dangerous patterns (approval-gated) ─────────────────────────────

    /** Patterns matching commands that are dangerous but may be intentional. */
    private static final List<Pattern> DANGEROUS_PATTERNS =
            List.of(
                    Pattern.compile(
                            "\\brm\\s+(-[^\\s]*r[^\\s]*\\s+)(/etc|/usr|/var|/boot|/sys|/proc|/opt)"),
                    Pattern.compile("\\bchmod\\s+(777|666)\\s+/"),
                    Pattern.compile("\\bchown\\s+(-[^\\s]*)?R?\\s+root"),
                    Pattern.compile("\\b(shutdown|reboot|halt|poweroff)\\b"),
                    Pattern.compile("\\bkill\\s+(-9\\s+|.*-KILL\\s+)[01]\\b"),
                    Pattern.compile("\\bgit\\s+push\\s+.*--force"),
                    Pattern.compile("\\bgit\\s+push\\s+-f\\b"),
                    Pattern.compile("\\bgit\\s+reset\\s+--hard"),
                    Pattern.compile("\\bgit\\s+clean\\s+-f"),
                    Pattern.compile("\\bgit\\s+branch\\s+-D\\b"));

    /** Optional custom blocklist loaded from {@code KAIRO_COMMAND_SAFETY_BLOCKLIST} env var. */
    private static final Pattern CUSTOM_BLOCKLIST = loadCustomBlocklist();

    // ── Singleton ───────────────────────────────────────────────────────────────

    private static final CommandSafetyPolicy INSTANCE = new CommandSafetyPolicy();

    private CommandSafetyPolicy() {}

    /** Returns the shared singleton instance. */
    public static CommandSafetyPolicy instance() {
        return INSTANCE;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Checks whether a command matches any catastrophic (Tier 1) pattern.
     *
     * <p>Phase 1: checks the full (unsplit) command against all catastrophic patterns — this
     * catches patterns that span segment delimiters (e.g. fork bomb contains {@code |} and {@code
     * ;}).
     *
     * <p>Phase 2: splits the command on {@code &&}, {@code ;}, and {@code |}, and checks each
     * segment against blocked prefixes (privilege escalation).
     *
     * @param command the shell command to validate
     * @return an error reason if the command is catastrophic; {@link Optional#empty()} if safe
     */
    public Optional<String> checkCatastrophic(String command) {
        if (DISABLED) {
            return Optional.empty();
        }
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }

        String trimmed = command.strip();

        // Phase 1: Check FULL command against all catastrophic patterns
        // (catches patterns that span segment delimiters like fork bomb)
        for (Pattern p : CATASTROPHIC_PATTERNS) {
            if (p.matcher(trimmed).find()) {
                return Optional.of(
                        "Blocked: catastrophic command detected ("
                                + p.pattern()
                                + "). This operation would cause irreversible damage.");
            }
        }

        // Phase 2: Check each segment for blocked prefixes (sudo, su, doas)
        String[] segments = trimmed.split("&&|;|\\|");
        for (String seg : segments) {
            String s = seg.strip().toLowerCase();
            if (s.isEmpty()) {
                continue;
            }
            for (String prefix : BLOCKED_PREFIXES) {
                if (s.startsWith(prefix)) {
                    return Optional.of(
                            "Blocked: command requires elevated privileges ("
                                    + prefix.trim()
                                    + "). This is not allowed for safety reasons.");
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Returns {@code true} if the command matches any dangerous (Tier 2) pattern or the custom
     * blocklist.
     *
     * @param command the shell command to check
     * @return {@code true} if the command is considered dangerous
     */
    public boolean isDangerous(String command) {
        if (DISABLED) {
            return false;
        }
        if (command == null || command.isBlank()) {
            return false;
        }

        for (Pattern p : DANGEROUS_PATTERNS) {
            if (p.matcher(command).find()) {
                return true;
            }
        }
        return CUSTOM_BLOCKLIST != null && CUSTOM_BLOCKLIST.matcher(command).find();
    }

    /**
     * Builds a human-readable explanation of why the command is considered dangerous.
     *
     * @param command the shell command that was flagged
     * @return a descriptive reason string
     */
    public String buildDangerReason(String command) {
        StringBuilder sb = new StringBuilder("Command is flagged as potentially dangerous: ");
        if (command.matches("(?s).*\\brm\\s+.*-[^\\s]*r.*")) {
            sb.append("recursive/forced file deletion targeting system paths");
        } else if (command.matches("(?s).*\\bchmod\\s+(777|666)\\s+/.*")) {
            sb.append("overly permissive file permissions on system paths");
        } else if (command.matches("(?s).*\\bchown\\s+.*root.*")) {
            sb.append("recursive ownership change to root");
        } else if (command.matches("(?s).*\\b(shutdown|reboot|halt|poweroff)\\b.*")) {
            sb.append("system shutdown/reboot command");
        } else if (command.matches("(?s).*\\bkill\\s+.*(-9|-KILL).*")) {
            sb.append("forceful kill of system process");
        } else if (command.matches("(?s).*\\bgit\\s+push\\s+.*(--force|-f).*")) {
            sb.append("git force push — may overwrite remote history");
        } else if (command.matches("(?s).*\\bgit\\s+reset\\s+--hard.*")) {
            sb.append("git hard reset — discards uncommitted changes");
        } else if (command.matches("(?s).*\\bgit\\s+clean\\s+-f.*")) {
            sb.append("git clean force — removes untracked files permanently");
        } else if (command.matches("(?s).*\\bgit\\s+branch\\s+-D.*")) {
            sb.append("git force branch deletion — may lose unmerged commits");
        } else {
            sb.append("matched custom blocklist pattern");
        }
        return sb.toString();
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private static Pattern loadCustomBlocklist() {
        String env = System.getenv("KAIRO_COMMAND_SAFETY_BLOCKLIST");
        if (env == null || env.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(env);
        } catch (Exception e) {
            log.warn("Invalid KAIRO_COMMAND_SAFETY_BLOCKLIST pattern: {}", env, e);
            return null;
        }
    }
}
