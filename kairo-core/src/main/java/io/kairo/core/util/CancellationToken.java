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
package io.kairo.core.util;

import io.kairo.api.agent.CancellationSignal;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Concrete {@link CancellationSignal} implementation backed by an {@link AtomicBoolean}.
 *
 * <p>Threaded from {@code DefaultReActAgent.interrupt()} through {@code ReActLoop} to {@code
 * ModelProvider} streaming calls. When cancelled, active streaming subscriptions abort immediately
 * without waiting for the stream to complete naturally.
 *
 * <p>Matches Claude Code's AbortController pattern for responsive task cancellation.
 */
public final class CancellationToken implements CancellationSignal {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** Signal cancellation. Safe to call multiple times. */
    public void cancel() {
        cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Create a non-cancelled token. */
    public static CancellationToken notCancelled() {
        return new CancellationToken();
    }
}
