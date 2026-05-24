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
package io.kairo.expertteam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.kairo.api.agent.Agent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExpertTeamComposerTest {

    @Test
    void create_buildsAllFourParts() {
        AtomicInteger calls = new AtomicInteger();
        var composition =
                ExpertTeamComposer.create(
                        3,
                        () -> {
                            calls.incrementAndGet();
                            return mock(Agent.class);
                        });
        assertThat(composition.coordinator()).isNotNull();
        assertThat(composition.roleRegistry()).isNotNull();
        assertThat(composition.messageBus()).isNotNull();
        assertThat(composition.agents()).hasSize(3);
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void create_rejectsZeroOrNegativeCount() {
        assertThatThrownBy(() -> ExpertTeamComposer.create(0, () -> mock(Agent.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentCount");
    }

    @Test
    void create_rejectsNullSupplier() {
        assertThatThrownBy(() -> ExpertTeamComposer.create(1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentSupplier");
    }

    @Test
    void create_rejectsNullFromSupplier() {
        assertThatThrownBy(() -> ExpertTeamComposer.create(2, () -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned null");
    }

    @Test
    void noOpMessageBus_doesNotThrowOnSendOrReceive() {
        var bus = ExpertTeamComposer.noOpMessageBus();
        // send / broadcast resolve to empty Mono — block() returns null without error
        bus.send("a", "b", null).block();
        bus.broadcast("a", null).block();
        // receive yields empty Flux — collectList resolves immediately
        assertThat(bus.receive("a").collectList().block()).isEmpty();
    }

    @Test
    void compositionAgentList_isImmutable() {
        var composition = ExpertTeamComposer.create(2, () -> mock(Agent.class));
        assertThatThrownBy(() -> composition.agents().add(mock(Agent.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
