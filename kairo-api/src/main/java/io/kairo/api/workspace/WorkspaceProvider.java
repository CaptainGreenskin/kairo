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
package io.kairo.api.workspace;

import io.kairo.api.Stable;

/**
 * Materialises a {@link Workspace} for a given {@link WorkspaceRequest}.
 *
 * <p>Implementations are responsible for:
 *
 * <ul>
 *   <li>resolving {@link WorkspaceRequest#hint()} into a real backing resource (local directory,
 *       remote git checkout, sandbox mount, …);
 *   <li>returning a {@link Workspace} whose {@link Workspace#root()} is valid until {@link
 *       #release(String)} is called for the same id;
 *   <li>idempotency: calling {@link #acquire(WorkspaceRequest)} twice with the same hint MAY return
 *       the same {@link Workspace} instance (the bundled {@code LocalDirectoryWorkspaceProvider}
 *       does this; remote-checkout providers are not required to).
 * </ul>
 *
 * <p>Implementations MUST be safe for concurrent invocation. They SHOULD NOT throw checked
 * exceptions; failure modes are surfaced via {@link RuntimeException} subtypes scoped to the
 * provider package.
 *
 * @since v1.1
 */
@Stable(value = "WorkspaceProvider SPI added in v1.1", since = "1.1.0")
public interface WorkspaceProvider {

    /**
     * Acquire a workspace for the given request. Never returns null.
     *
     * @throws RuntimeException provider-specific failure (resolution, IO, permission). Callers
     *     SHOULD treat this as a non-recoverable acquisition error and surface the message to the
     *     user.
     */
    Workspace acquire(WorkspaceRequest request);

    /**
     * Release a previously-acquired workspace. After this call returns, the workspace's {@link
     * Workspace#root()} MAY become invalid. Calling release with an unknown id is a no-op.
     */
    void release(String workspaceId);
}
