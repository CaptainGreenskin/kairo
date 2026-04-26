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
package io.kairo.api.sandbox;

import io.kairo.api.Stable;
import io.kairo.api.tenant.TenantContext;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable description of a single sandbox execution.
 *
 * <p>{@code SandboxRequest} bundles every piece of context an {@link ExecutionSandbox} needs to run
 * a command: the shell line itself, the workspace root the command sees as its working directory,
 * the environment delta, the timeout / output budget, the active {@link TenantContext} for
 * cross-cutting attribution, and a read-only flag the backend uses to decide whether to mount the
 * workspace writable.
 *
 * <p>The {@code env} and {@code workspaceRoot} components are wired by the framework — typically
 * {@code BashTool} reads the workspace from {@link io.kairo.api.tool.ToolContext} and forwards it
 * here, so multiple agents can run concurrent commands against different workspaces without
 * stepping on each other.
 *
 * @param command the shell command line to execute (e.g., {@code "ls -la && wc -l *.java"})
 * @param workspaceRoot working directory the command sees; never null
 * @param env additional environment variables; defensively copied, never null
 * @param timeout maximum duration before the backend kills the process; never null
 * @param maxOutputBytes maximum combined stdout + stderr bytes captured before truncation
 * @param tenant active tenant for cross-cutting attribution; never null
 * @param readOnly when {@code true}, the backend MUST refuse or sandbox writes to {@code
 *     workspaceRoot}
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Sandbox request envelope added in v1.1")
public record SandboxRequest(
        String command,
        Path workspaceRoot,
        Map<String, String> env,
        Duration timeout,
        long maxOutputBytes,
        TenantContext tenant,
        boolean readOnly) {

    public SandboxRequest {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(tenant, "tenant");
        if (command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxOutputBytes <= 0) {
            throw new IllegalArgumentException("maxOutputBytes must be positive");
        }
        env = env == null ? Map.of() : Map.copyOf(env);
    }

    /**
     * Mutable builder with defaults: empty env, 30 s timeout, 1 MiB output budget, {@link
     * TenantContext#SINGLE} tenant, writable workspace.
     */
    public static final class Builder {
        private final String command;
        private final Path workspaceRoot;
        private Map<String, String> env = Map.of();
        private Duration timeout = Duration.ofSeconds(30);
        private long maxOutputBytes = 1L << 20;
        private TenantContext tenant = TenantContext.SINGLE;
        private boolean readOnly = false;

        public Builder(String command, Path workspaceRoot) {
            this.command = command;
            this.workspaceRoot = workspaceRoot;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxOutputBytes(long maxOutputBytes) {
            this.maxOutputBytes = maxOutputBytes;
            return this;
        }

        public Builder tenant(TenantContext tenant) {
            this.tenant = tenant;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public SandboxRequest build() {
            return new SandboxRequest(
                    command, workspaceRoot, env, timeout, maxOutputBytes, tenant, readOnly);
        }
    }
}
