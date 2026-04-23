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

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.kairo.api.agent.AgentState;
import io.kairo.api.hook.HookHandler;
import io.kairo.api.hook.HookPhase;
import io.kairo.api.hook.SessionEndEvent;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HookHandlerDispatchTest {

    static class Listener {
        final AtomicInteger count = new AtomicInteger();

        @HookHandler(HookPhase.SESSION_END)
        public SessionEndEvent onEnd(SessionEndEvent event) {
            count.incrementAndGet();
            return event;
        }
    }

    @Test
    void unifiedHookHandlerReceivesSessionEndEvent() {
        DefaultHookChain chain = new DefaultHookChain();
        Listener listener = new Listener();
        chain.register(listener);

        SessionEndEvent event =
                new SessionEndEvent(
                        "agent", AgentState.COMPLETED, 3, 100, Duration.ofSeconds(1), null);

        chain.fireOnSessionEnd(event).block();
        assertEquals(1, listener.count.get());
    }
}
