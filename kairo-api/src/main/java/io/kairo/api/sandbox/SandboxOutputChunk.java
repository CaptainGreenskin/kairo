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
import java.util.Objects;

/**
 * A single chunk of bytes emitted by a running sandbox process.
 *
 * <p>Chunk granularity is implementation-defined: backends MAY emit one chunk per line, per
 * fixed-size buffer, or per syscall — consumers MUST treat boundaries as opaque. The {@link Stdout}
 * / {@link Stderr} discriminator preserves stream identity even when callers (notably {@code
 * BashTool}) merge them for display.
 *
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Sandbox output chunk added in v1.1")
public sealed interface SandboxOutputChunk {

    /** Bytes from the process's standard output stream. */
    byte[] data();

    /** A chunk emitted on stdout. The {@code data} array is owned by the chunk; do not mutate. */
    record Stdout(byte[] data) implements SandboxOutputChunk {
        public Stdout {
            Objects.requireNonNull(data, "data");
        }
    }

    /** A chunk emitted on stderr. The {@code data} array is owned by the chunk; do not mutate. */
    record Stderr(byte[] data) implements SandboxOutputChunk {
        public Stderr {
            Objects.requireNonNull(data, "data");
        }
    }
}
