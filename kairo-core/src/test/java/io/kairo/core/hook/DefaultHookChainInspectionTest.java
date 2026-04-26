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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultHookChainInspectionTest {

    private DefaultHookChain chain;

    @BeforeEach
    void setUp() {
        chain = new DefaultHookChain();
    }

    @Test
    void emptyWhenNoHandlersRegistered() {
        assertThat(chain.getRegisteredHandlers()).isEmpty();
    }

    @Test
    void returnsRegisteredHandler() {
        Object handler = new Object();
        chain.register(handler);

        List<Object> handlers = chain.getRegisteredHandlers();

        assertThat(handlers).hasSize(1).contains(handler);
    }

    @Test
    void multipleHandlersReturnedInOrder() {
        Object h1 = new Object();
        Object h2 = new Object();
        Object h3 = new Object();
        chain.register(h1);
        chain.register(h2);
        chain.register(h3);

        assertThat(chain.getRegisteredHandlers()).containsExactly(h1, h2, h3);
    }

    @Test
    void unregisteredHandlerRemovedFromList() {
        Object h1 = new Object();
        Object h2 = new Object();
        chain.register(h1);
        chain.register(h2);
        chain.unregister(h1);

        assertThat(chain.getRegisteredHandlers()).containsExactly(h2);
    }

    @Test
    void returnedListIsUnmodifiable() {
        chain.register(new Object());

        List<Object> handlers = chain.getRegisteredHandlers();

        assertThatThrownBy(() -> handlers.add(new Object()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullHandlerIgnoredOnRegister() {
        chain.register(null);

        assertThat(chain.getRegisteredHandlers()).isEmpty();
    }
}
