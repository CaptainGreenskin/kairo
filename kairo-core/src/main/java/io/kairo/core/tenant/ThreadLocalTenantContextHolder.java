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
package io.kairo.core.tenant;

import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tenant.TenantContextHolder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Thread-local default implementation of {@link TenantContextHolder}.
 *
 * <p>Bindings nest using a per-thread stack: the top of the stack is what {@link #current()}
 * returns, and closing a {@link TenantContextHolder.Scope} pops exactly the value that scope
 * pushed. Out-of-order close is detected and raises {@link IllegalStateException}.
 *
 * <p>This holder is safe for concurrent use; each thread has its own stack. Carrying a tenant
 * across thread boundaries (e.g., {@link java.util.concurrent.Executor}) requires an explicit
 * propagator — Reactor and Loom bridges live in their respective modules.
 *
 * @since v1.1
 */
public final class ThreadLocalTenantContextHolder implements TenantContextHolder {

    private final ThreadLocal<Deque<TenantContext>> stack =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Override
    public TenantContext current() {
        Deque<TenantContext> s = stack.get();
        TenantContext top = s.peek();
        return top != null ? top : TenantContext.SINGLE;
    }

    @Override
    public Scope bind(TenantContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        Deque<TenantContext> s = stack.get();
        s.push(ctx);
        return new StackScope(s, ctx);
    }

    private static final class StackScope implements Scope {
        private final Deque<TenantContext> owner;
        private final TenantContext expected;
        private boolean closed;

        StackScope(Deque<TenantContext> owner, TenantContext expected) {
            this.owner = owner;
            this.expected = expected;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            TenantContext top = owner.peek();
            if (top != expected) {
                throw new IllegalStateException(
                        "TenantContext scope closed out of order: expected "
                                + expected
                                + " but found "
                                + top);
            }
            owner.pop();
        }
    }
}
