/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.guardrail.policy;

import java.util.regex.Pattern;

/**
 * Categorises a bash command line by its likely side-effect profile.
 *
 * <p>Counterpart to Claude Code's {@code bashClassifier} — but regex-only and synchronous. The
 * LLM-driven path (Anthropic's {@code yoloClassifier.ts} is 1496 lines and costs an extra model
 * call per bash invocation) is intentionally not in scope here; this classifier hands a category to
 * the existing {@link DangerousCommandPolicy} for fast permission/audit decisions, and the future
 * {@code LlmBashClassifier} can fall back to a model only when {@link Category#UNKNOWN} comes back.
 *
 * <p>Why categories instead of just boolean "safe / unsafe": the same command may be allowed under
 * different agent modes. A {@link Category#WRITE} command auto-approves in {@code BYPASS}
 * permission mode but requires confirmation in {@code STRICT}; {@link Category#NETWORK} is fine in
 * a research-only agent but blocked in a sandboxed eval. Surfacing the category lets the permission
 * system make those distinctions without re-parsing the command at every layer.
 *
 * <p>Heuristic ordering: patterns are checked DESTRUCTIVE → EXEC → NETWORK → WRITE → READ_ONLY,
 * because a single command line can match multiple buckets (e.g. {@code curl ... | bash} is both
 * NETWORK and EXEC, but EXEC is the bucket that matters for auth decisions).
 *
 * @since 1.3
 */
public final class BashCommandClassifier {

    /** Side-effect taxonomy for a single command line. */
    public enum Category {
        /**
         * Pure information retrieval: {@code ls}, {@code cat}, {@code grep}, {@code git status},
         * {@code echo}, {@code pwd}. Safe to auto-approve in every mode.
         */
        READ_ONLY,

        /**
         * Local filesystem mutation: {@code mkdir}, {@code touch}, {@code rm}, {@code cp}, {@code
         * git commit}, {@code npm install}. Default-deny in strict mode.
         */
        WRITE,

        /**
         * Network egress: {@code curl}, {@code wget}, {@code git push}, {@code npm publish}.
         * Sandboxed evals usually want to block this; research agents allow.
         */
        NETWORK,

        /**
         * Arbitrary code execution from untrusted input: {@code curl | sh}, {@code eval}, piping a
         * {@code wget} into a shell. Should always trigger explicit confirmation.
         */
        EXEC,

        /**
         * Destructive at a level beyond "remove this file": {@code rm -rf /}, {@code mkfs}, {@code
         * dd to /dev/sd*}, {@code DROP TABLE}. Block hardline regardless of mode.
         */
        DESTRUCTIVE,

        /**
         * No classifier rule matched. Caller may consult an LLM, fall back to a default policy, or
         * surface to the user for explicit decision.
         */
        UNKNOWN
    }

    // ── Destructive (hardline) ────────────────────────────────────────────────
    // Same regex family as DangerousCommandPolicy so the two stay in sync.
    private static final Pattern[] DESTRUCTIVE = {
        Pattern.compile(
                "(?i)\\brm\\s+(-[a-zA-Z]*r[a-zA-Z]*\\s+|-r\\s+|--recursive\\s+)[^\\s]*/\\s*"),
        Pattern.compile("(?i)\\brm\\s+(-[a-zA-Z]*[fr][a-zA-Z]*\\s+){1,}/\\s*$"),
        Pattern.compile("(?i)\\bmkfs(\\.|\\s)"),
        Pattern.compile("(?i)\\bdd\\s+.*\\bof=/dev/(sd|nvme|hd)"),
        Pattern.compile("(?i)\\bDROP\\s+TABLE\\b"),
        Pattern.compile("(?i)\\bTRUNCATE\\s+TABLE\\b"),
        // Use \w+\b (not bare \w+) so the engine can't backtrack to a shorter word boundary
        // and bypass the negative lookahead — without it, "DELETE FROM users WHERE id=5"
        // shortens \w+ to "user" and the lookahead at position 's' sees no "\s+WHERE".
        Pattern.compile("(?i)\\bDELETE\\s+FROM\\s+\\w+\\b(?!\\s+WHERE)"),
        // fork bomb
        Pattern.compile(":\\(\\)\\s*\\{[^}]*\\|\\s*:[^}]*\\}\\s*;\\s*:"),
        // disk wipe
        Pattern.compile("(?i)\\bshred\\b.*-z"),
    };

    // ── Exec from untrusted input ─────────────────────────────────────────────
    private static final Pattern[] EXEC = {
        // curl|sh, wget|bash, etc.
        Pattern.compile("(?i)\\b(curl|wget|fetch)\\b[^|]*\\|\\s*(sh|bash|zsh|ksh|sudo)\\b"),
        // sudo into a pipe is also EXEC
        Pattern.compile("(?i)\\bsudo\\s+[^|]*\\|\\s*(sh|bash|zsh)\\b"),
        // eval / source from variable
        Pattern.compile("(?i)\\beval\\s+\"?\\$"),
        Pattern.compile("(?i)\\bsource\\s+<\\(curl\\b"),
        // kill all processes
        Pattern.compile("(?i)\\bkill\\s+-9\\s+-1\\b"),
    };

    // ── Network egress ────────────────────────────────────────────────────────
    private static final Pattern[] NETWORK = {
        Pattern.compile("(?i)\\b(curl|wget|fetch|http|https)\\b"),
        Pattern.compile("(?i)\\bgit\\s+(push|fetch|pull|clone|remote)\\b"),
        Pattern.compile("(?i)\\bnpm\\s+(publish|install\\s+--save|i\\b)"),
        Pattern.compile("(?i)\\bpip\\s+install\\b"),
        Pattern.compile("(?i)\\bnc\\b\\s+[a-zA-Z0-9.-]+\\s+\\d+"),
        Pattern.compile("(?i)\\bssh\\s+[^@]*@"),
        Pattern.compile("(?i)\\bscp\\s+"),
        Pattern.compile("(?i)\\brsync\\b.*::"),
    };

    // ── Write (local filesystem mutation) ─────────────────────────────────────
    private static final Pattern[] WRITE = {
        Pattern.compile("(?i)\\b(mkdir|touch|cp|mv|ln|chmod|chown)\\b"),
        // rm of specific files (non-destructive — caught by DESTRUCTIVE first if rm -rf /)
        Pattern.compile("(?i)\\brm\\b"),
        // shell redirects to a real file
        Pattern.compile(">>?\\s*[^\\s&]+"),
        // git mutating subcommands
        Pattern.compile(
                "(?i)\\bgit\\s+(add|commit|merge|rebase|reset|checkout|switch|stash|tag|branch\\s+-)\\b"),
        // package install (local mutation; network already caught above)
        Pattern.compile("(?i)\\b(apt|yum|brew|dnf|pacman|zypper)\\s+(install|remove|upgrade)\\b"),
        // file edits via heredoc / sed -i
        Pattern.compile("(?i)\\bsed\\s+-i\\b"),
    };

    // ── Read-only ────────────────────────────────────────────────────────────
    private static final Pattern[] READ_ONLY = {
        Pattern.compile("(?i)^\\s*(ls|cat|head|tail|less|more|file|stat|wc)\\b"),
        Pattern.compile("(?i)^\\s*(pwd|echo|printf|true|false|date|whoami|id|hostname|uname)\\b"),
        Pattern.compile("(?i)^\\s*(grep|find|locate|which|whereis|type)\\b"),
        Pattern.compile("(?i)^\\s*git\\s+(status|log|diff|show|blame|reflog|describe)\\b"),
        Pattern.compile("(?i)^\\s*(ps|top|htop|du|df|free)\\b"),
    };

    private BashCommandClassifier() {}

    /**
     * Classify a single command line. The input may include leading {@code sudo}, environment
     * variable prefixes ({@code FOO=bar cmd}), and trailing pipes/redirects — the regex set is
     * permissive enough that those don't strip the signal.
     *
     * @param command the bash command line; {@code null} or blank → {@link Category#UNKNOWN}
     * @return the most severe category that matches; never null
     */
    public static Category classify(String command) {
        if (command == null || command.isBlank()) {
            return Category.UNKNOWN;
        }
        // Order is significant — see class javadoc.
        if (matchesAny(command, DESTRUCTIVE)) return Category.DESTRUCTIVE;
        if (matchesAny(command, EXEC)) return Category.EXEC;
        if (matchesAny(command, NETWORK)) return Category.NETWORK;
        if (matchesAny(command, WRITE)) return Category.WRITE;
        if (matchesAny(command, READ_ONLY)) return Category.READ_ONLY;
        return Category.UNKNOWN;
    }

    private static boolean matchesAny(String command, Pattern[] patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(command).find()) {
                return true;
            }
        }
        return false;
    }
}
