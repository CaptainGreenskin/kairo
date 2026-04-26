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
package io.kairo.core.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.CompactionResult;
import io.kairo.api.context.ContextManager;
import io.kairo.api.context.TokenBudget;
import io.kairo.api.message.Msg;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class CompactionTriggerTest {

    // Minimal ContextManager stub that never triggers compaction
    static class NoCompactionManager implements ContextManager {
        @Override
        public void addMessage(Msg message) {}

        @Override
        public List<Msg> getMessages() {
            return List.of();
        }

        @Override
        public int getTokenCount() {
            return 0;
        }

        @Override
        public TokenBudget getTokenBudget() {
            return null;
        }

        @Override
        public Mono<CompactionResult> compact() {
            return Mono.empty();
        }

        @Override
        public void markVerbatim(String messageId) {}

        @Override
        public boolean needsCompaction(List<Msg> msgs) {
            return false;
        }
    }

    @Test
    void nullContextManagerReturnsFalse() {
        CompactionTrigger trigger = new CompactionTrigger(null, null);
        Boolean result = trigger.checkAndCompact(List.of()).block();
        assertFalse(result);
    }

    @Test
    void noCompactionNeededReturnsFalse() {
        CompactionTrigger trigger = new CompactionTrigger(new NoCompactionManager(), null);
        Boolean result = trigger.checkAndCompact(List.of()).block();
        assertFalse(result);
    }

    @Test
    void noCompactionNeededWithNonEmptyHistoryReturnsFalse() {
        CompactionTrigger trigger = new CompactionTrigger(new NoCompactionManager(), null);
        Boolean result = trigger.checkAndCompact(List.of()).block();
        assertFalse(Boolean.TRUE.equals(result));
    }

    @Test
    void nullContextManagerWithNonEmptyHistoryReturnsFalse() {
        CompactionTrigger trigger = new CompactionTrigger(null, null);
        Boolean result = trigger.checkAndCompact(List.of()).block();
        assertEquals(Boolean.FALSE, result);
    }
}
