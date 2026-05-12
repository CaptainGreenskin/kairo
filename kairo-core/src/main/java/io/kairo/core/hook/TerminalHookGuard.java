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
package io.kairo.core.hook;

import io.kairo.api.hook.HookChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Guarantees exactly-once terminal hook firing. Second and subsequent calls are swallowed with a
 * DEBUG log.
 *
 * <p>The race between onComplete/onError/cancel is an <em>expected</em> reactive lifecycle path,
 * not a bug — hence DEBUG, not WARN.
 */
public class TerminalHookGuard {
    private static final Logger log = LoggerFactory.getLogger(TerminalHookGuard.class);
    private final AtomicBoolean fired = new AtomicBoolean(false);
    private final HookChain hookChain;

    public TerminalHookGuard(HookChain hookChain) {
        this.hookChain = hookChain;
    }

    /**
     * Fire session-end hook exactly once. Second and subsequent calls return {@code Mono.empty()} —
     * caller knows emission was suppressed.
     */
    public <T> Mono<T> fireSessionEndOnce(T event) {
        if (fired.compareAndSet(false, true)) {
            return hookChain.fireOnSessionEnd(event);
        }
        log.debug("Terminal hook already fired — suppressing duplicate emission");
        return Mono.empty();
    }

    public boolean hasFired() {
        return fired.get();
    }
}
