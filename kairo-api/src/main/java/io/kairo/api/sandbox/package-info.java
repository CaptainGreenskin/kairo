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
/**
 * Execution Sandbox SPI — pluggable backend for running untrusted shell commands on behalf of tools
 * (notably {@code BashTool}).
 *
 * <p>v1.1 promotes the {@link java.lang.ProcessBuilder ProcessBuilder} call previously hard-coded
 * inside {@code BashTool} to a SPI seam so that container, micro-VM, and remote-execution backends
 * can be plugged in without touching tool code. The bundled {@code LocalProcessSandbox} (in {@code
 * kairo-tools}) keeps single-process behaviour byte-for-byte compatible with v1.0.
 *
 * <h2>Design principles</h2>
 *
 * <ul>
 *   <li><b>Default = local process</b>. When no {@link io.kairo.api.sandbox.ExecutionSandbox} is
 *       bound through {@link io.kairo.api.tool.ToolContext}, {@code BashTool} falls back to the
 *       in-process {@code LocalProcessSandbox}; existing call sites observe zero behaviour change.
 *   <li><b>Behaviour contract is on the sandbox, not the tool</b>. Timeout enforcement, output
 *       truncation at {@link io.kairo.api.sandbox.SandboxRequest#maxOutputBytes() maxOutputBytes},
 *       working-directory selection, and {@code readOnly} workspace mounting are sandbox
 *       responsibilities — every backend MUST honour them and is verified by {@code
 *       ExecutionSandboxTCK}.
 *   <li><b>Streaming output, batched compatibility</b>. {@link
 *       io.kairo.api.sandbox.SandboxHandle#output() SandboxHandle.output()} is a hot {@code Flux},
 *       but {@code BashTool} drains it into a single string for backward compatibility. Future
 *       tools can subscribe directly for progressive output.
 * </ul>
 *
 * <h2>Stability</h2>
 *
 * <p>Types in this package are {@link io.kairo.api.Stable} since 1.1.0.
 *
 * @since v1.1
 */
package io.kairo.api.sandbox;
