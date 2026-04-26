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
 * Workspace SPI — explicit working-directory context for agents and tools.
 *
 * <p>v1.1 promotes "workspace" from an implicit {@code System.getProperty("user.dir")} to a
 * first-class context object so a single agent can manage multiple workspaces concurrently (e.g.,
 * reviewing five PRs at once, each with its own checkout).
 *
 * <h2>Design principles</h2>
 *
 * <ul>
 *   <li><b>Default = cwd</b>. The bundled {@code LocalDirectoryWorkspaceProvider} returns a
 *       workspace rooted at the JVM's working directory when no hint is supplied; existing call
 *       paths see zero behaviour change.
 *   <li><b>Explicit over ambient</b>. File tools ({@code Read} / {@code Write} / {@code Edit} /
 *       {@code Glob} / {@code Grep}) resolve relative paths against the active workspace's root,
 *       not against {@code user.dir}.
 *   <li><b>Backend-agnostic</b>. {@link io.kairo.api.workspace.WorkspaceKind} models LOCAL,
 *       REMOTE_GIT, and EPHEMERAL backends. v1.1 ships LOCAL only; remote git checkouts and S3
 *       overlays are tracked for v1.3.
 * </ul>
 *
 * <h2>Stability</h2>
 *
 * <p>Types in this package are {@link io.kairo.api.Stable} since 1.1.0.
 *
 * @since v1.1
 */
package io.kairo.api.workspace;
