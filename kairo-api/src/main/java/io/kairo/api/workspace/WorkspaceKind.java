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
 * Backend taxonomy for a {@link Workspace}.
 *
 * <p>v1.1 ships {@link #LOCAL} only. {@link #REMOTE_GIT} and {@link #EPHEMERAL} are reserved for
 * v1.3 (remote git checkout providers, ephemeral sandbox-backed workspaces).
 *
 * @since v1.1
 */
@Stable(value = "WorkspaceKind enum surface fixed at GA", since = "1.1.0")
public enum WorkspaceKind {

    /**
     * Local filesystem directory; {@link Workspace#root()} is a real {@link java.nio.file.Path}.
     */
    LOCAL,

    /**
     * Workspace materialised from a remote git repository. {@link Workspace#root()} points to the
     * local checkout directory; {@link Workspace#metadata()} typically carries {@code git.remote},
     * {@code git.branch}, {@code git.commit}.
     */
    REMOTE_GIT,

    /**
     * Short-lived workspace, typically backed by an {@code ExecutionSandbox} or a temp directory.
     * The root may not survive process restart and should be released explicitly.
     */
    EPHEMERAL
}
