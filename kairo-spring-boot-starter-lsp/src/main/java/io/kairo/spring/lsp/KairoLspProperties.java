/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.spring.lsp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kairo.lsp")
public class KairoLspProperties {

    /** Whether to wire up the LSP subsystem at all. Default: false (opt-in). */
    private boolean enabled = false;

    /** Whether to register the five built-in language servers automatically. */
    private boolean registerBuiltIns = true;

    /** Idle reaper window for pooled clients. */
    private Duration idleTimeout = Duration.ofMinutes(10);

    /** Default wait for publishDiagnostics after an edit. */
    private Duration diagnosticsTimeout = Duration.ofSeconds(3);

    /** When true, restrict the service to files inside a git worktree (default). */
    private boolean gitWorktreeOnly = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRegisterBuiltIns() {
        return registerBuiltIns;
    }

    public void setRegisterBuiltIns(boolean registerBuiltIns) {
        this.registerBuiltIns = registerBuiltIns;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Duration getDiagnosticsTimeout() {
        return diagnosticsTimeout;
    }

    public void setDiagnosticsTimeout(Duration diagnosticsTimeout) {
        this.diagnosticsTimeout = diagnosticsTimeout;
    }

    public boolean isGitWorktreeOnly() {
        return gitWorktreeOnly;
    }

    public void setGitWorktreeOnly(boolean gitWorktreeOnly) {
        this.gitWorktreeOnly = gitWorktreeOnly;
    }
}
