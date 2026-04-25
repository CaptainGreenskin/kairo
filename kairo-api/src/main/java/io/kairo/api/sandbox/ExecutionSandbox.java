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

/**
 * Backend abstraction for executing untrusted shell commands on behalf of a tool.
 *
 * <p>The bundled {@code LocalProcessSandbox} (in {@code kairo-tools}) wraps {@link
 * java.lang.ProcessBuilder} and is the default for single-process, single-host deployments. Adapter
 * authors can implement this interface to swap in container, micro-VM, or remote execution backends
 * without changing tool code: {@code BashTool} resolves the active sandbox from {@link
 * io.kairo.api.tool.ToolContext} and falls back to {@code LocalProcessSandbox} when none is bound.
 *
 * <p>Implementations are responsible for honouring the {@link SandboxRequest} contract: apply the
 * timeout (kill on expiry, surface {@link SandboxExit#timedOut()}), enforce {@link
 * SandboxRequest#maxOutputBytes()} truncation, and respect {@link SandboxRequest#readOnly()} — a
 * writable workspace MUST be writable, a read-only request MUST refuse writes (typically by
 * mounting the workspace read-only or rejecting at request validation time).
 *
 * <p>Implementations MUST be safe for concurrent {@link #start(SandboxRequest)} calls.
 *
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Execution sandbox SPI added in v1.1")
public interface ExecutionSandbox {

    /**
     * Starts the command described by {@code request} and returns a handle for streaming output and
     * observing the exit. The returned handle is hot — output streaming begins as soon as the
     * process is started.
     *
     * @throws NullPointerException if {@code request} is null
     */
    SandboxHandle start(SandboxRequest request);
}
