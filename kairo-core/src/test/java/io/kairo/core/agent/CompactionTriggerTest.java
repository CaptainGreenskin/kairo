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

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.context.ContextManager;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class CompactionTriggerTest {

    @Test
    void checkAndCompact_nullContextManager_returnsFalse() {
        // reactLoop not used when contextManager is null — safe to pass null
        var trigger = new CompactionTrigger(null, null);

        StepVerifier.create(trigger.checkAndCompact(List.of())).expectNext(false).verifyComplete();
    }

    @Test
    void checkAndCompact_contextManagerNotNeeded_returnsFalse() {
        var contextManager = mock(ContextManager.class);
        when(contextManager.needsCompaction(anyList())).thenReturn(false);

        // reactLoop not used when needsCompaction returns false
        var trigger = new CompactionTrigger(contextManager, null);

        StepVerifier.create(trigger.checkAndCompact(List.of(Msg.of(MsgRole.USER, "hello"))))
                .expectNext(false)
                .verifyComplete();
    }
}
