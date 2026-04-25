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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tenant.TenantContextHolder;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ThreadLocalTenantContextHolderTest {

    @Test
    void unboundCurrentReturnsSingleSentinel() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        assertSame(TenantContext.SINGLE, holder.current());
    }

    @Test
    void bindingExposesTenantWithinScopeAndRestoresOnClose() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        TenantContext acme = new TenantContext("acme", "alice", Map.of());

        try (TenantContextHolder.Scope ignored = holder.bind(acme)) {
            assertEquals(acme, holder.current());
        }

        assertSame(TenantContext.SINGLE, holder.current());
    }

    @Test
    void nestedBindingsStackAndUnwindInOrder() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        TenantContext outer = new TenantContext("outer", "u1", Map.of());
        TenantContext inner = new TenantContext("inner", "u2", Map.of());

        try (TenantContextHolder.Scope outerScope = holder.bind(outer)) {
            assertEquals(outer, holder.current());
            try (TenantContextHolder.Scope innerScope = holder.bind(inner)) {
                assertEquals(inner, holder.current());
            }
            assertEquals(outer, holder.current());
        }

        assertSame(TenantContext.SINGLE, holder.current());
    }

    @Test
    void outOfOrderCloseRaisesIllegalState() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        TenantContext outer = new TenantContext("outer", "u1", Map.of());
        TenantContext inner = new TenantContext("inner", "u2", Map.of());

        TenantContextHolder.Scope outerScope = holder.bind(outer);
        TenantContextHolder.Scope innerScope = holder.bind(inner);

        assertThrows(IllegalStateException.class, outerScope::close);
        innerScope.close();
        outerScope.close();
    }

    @Test
    void closeIsIdempotent() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        TenantContext acme = new TenantContext("acme", "alice", Map.of());
        TenantContextHolder.Scope scope = holder.bind(acme);
        scope.close();
        scope.close();
        assertSame(TenantContext.SINGLE, holder.current());
    }

    @Test
    void bindingsAreIsolatedAcrossThreads() throws Exception {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        TenantContext mainCtx = new TenantContext("main", "u-main", Map.of());
        TenantContext otherCtx = new TenantContext("other", "u-other", Map.of());

        try (TenantContextHolder.Scope ignored = holder.bind(mainCtx)) {
            CountDownLatch othersBoundLatch = new CountDownLatch(1);
            CountDownLatch mainCheckedLatch = new CountDownLatch(1);
            AtomicReference<TenantContext> observedOnOther = new AtomicReference<>();

            Thread t =
                    new Thread(
                            () -> {
                                try (TenantContextHolder.Scope s = holder.bind(otherCtx)) {
                                    observedOnOther.set(holder.current());
                                    othersBoundLatch.countDown();
                                    try {
                                        mainCheckedLatch.await();
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            });
            t.start();
            othersBoundLatch.await();
            assertEquals(mainCtx, holder.current());
            mainCheckedLatch.countDown();
            t.join();

            assertEquals(otherCtx, observedOnOther.get());
            assertEquals(mainCtx, holder.current());
        }
    }

    @Test
    void rejectsNullBinding() {
        ThreadLocalTenantContextHolder holder = new ThreadLocalTenantContextHolder();
        assertThrows(NullPointerException.class, () -> holder.bind(null));
    }
}
