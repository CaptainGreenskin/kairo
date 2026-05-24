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
package io.kairo.expertteam.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.event.KairoEvent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class RecordingEventBusTest {

    private RecordingEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new RecordingEventBus();
    }

    private static KairoEvent teamEvent(String type) {
        return KairoEvent.of(KairoEvent.DOMAIN_TEAM, type, null);
    }

    @Test
    void initiallyNoEventsRecorded() {
        assertThat(bus.recorded()).isEmpty();
    }

    @Test
    void publishSingleEventRecordsIt() {
        bus.publish(teamEvent("TEAM_STARTED"));

        assertThat(bus.recorded()).hasSize(1);
        assertThat(bus.recorded().get(0).eventType()).isEqualTo("TEAM_STARTED");
    }

    @Test
    void publishMultipleEventsRecordsAll() {
        bus.publish(teamEvent("A"));
        bus.publish(teamEvent("B"));
        bus.publish(teamEvent("C"));

        assertThat(bus.recorded()).hasSize(3);
    }

    @Test
    void publishPreservesOrder() {
        bus.publish(teamEvent("FIRST"));
        bus.publish(teamEvent("SECOND"));
        bus.publish(teamEvent("THIRD"));

        List<String> types = bus.recorded().stream().map(KairoEvent::eventType).toList();
        assertThat(types).containsExactly("FIRST", "SECOND", "THIRD");
    }

    @Test
    void publishNullIsIgnored() {
        bus.publish(null);
        assertThat(bus.recorded()).isEmpty();
    }

    @Test
    void recordedTeamEventsFiltersToTeamDomain() {
        bus.publish(teamEvent("TEAM_A"));
        bus.publish(KairoEvent.of("execution", "TOOL_CALLED", null));
        bus.publish(teamEvent("TEAM_B"));

        List<KairoEvent> teamEvents = bus.recordedTeamEvents();
        assertThat(teamEvents).hasSize(2);
        assertThat(teamEvents).extracting(KairoEvent::domain).containsOnly(KairoEvent.DOMAIN_TEAM);
    }

    @Test
    void teamEventTypesReturnsTypeNamesInOrder() {
        bus.publish(teamEvent("STEP_STARTED"));
        bus.publish(teamEvent("STEP_COMPLETED"));

        assertThat(bus.teamEventTypes()).containsExactly("STEP_STARTED", "STEP_COMPLETED");
    }

    @Test
    void clearRemovesAllRecordedEvents() {
        bus.publish(teamEvent("A"));
        bus.publish(teamEvent("B"));
        bus.clear();

        assertThat(bus.recorded()).isEmpty();
    }

    @Test
    void subscribeEmitsAllRecordedEvents() {
        bus.publish(teamEvent("E1"));
        bus.publish(teamEvent("E2"));

        StepVerifier.create(bus.subscribe()).expectNextCount(2).verifyComplete();
    }

    @Test
    void subscribeWithDomainFiltersEvents() {
        bus.publish(teamEvent("T1"));
        bus.publish(KairoEvent.of("execution", "X1", null));

        StepVerifier.create(bus.subscribe(KairoEvent.DOMAIN_TEAM))
                .expectNextMatches(e -> "T1".equals(e.eventType()))
                .verifyComplete();
    }

    @Test
    void subscribeEmptyBusCompletesImmediately() {
        StepVerifier.create(bus.subscribe()).verifyComplete();
    }

    @Test
    void recordedReturnsImmutableSnapshot() {
        bus.publish(teamEvent("A"));
        List<KairoEvent> snapshot = bus.recorded();

        bus.publish(teamEvent("B"));

        assertThat(snapshot).hasSize(1);
    }
}
