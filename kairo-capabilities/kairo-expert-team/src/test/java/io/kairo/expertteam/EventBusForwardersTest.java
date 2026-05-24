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

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.team.TeamEvent;
import io.kairo.api.team.TeamEventType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link EventBusForwarders} factory.
 *
 * <p>The forwarder's contract is narrow but easy to misread: TeamEvent payloads must reach the
 * consumer, anything else must be ignored, the consumer must not be able to break the publish call
 * (KairoEventBus contract = "never throws"), and the underlying sink must still replay events to
 * subscribers regardless of consumer outcome.
 */
class EventBusForwardersTest {

    @Test
    void toKairoEventBus_forwardsTeamEventPayloadsToConsumer() {
        List<TeamEvent> received = new ArrayList<>();
        KairoEventBus bus = EventBusForwarders.toKairoEventBus(received::add);

        TeamEvent te1 = teamEvent("team-1", "req-1", TeamEventType.TEAM_STARTED);
        TeamEvent te2 = teamEvent("team-1", "req-1", TeamEventType.STEP_COMPLETED);
        bus.publish(wrap(te1));
        bus.publish(wrap(te2));

        assertThat(received).containsExactly(te1, te2);
    }

    @Test
    void toKairoEventBus_ignoresEventsWithNonTeamEventPayload() {
        // Publishing a KairoEvent whose payload is not a TeamEvent must NOT reach the consumer —
        // but must still pass through the sink so other subscribers can see it.
        List<TeamEvent> received = new ArrayList<>();
        KairoEventBus bus = EventBusForwarders.toKairoEventBus(received::add);

        KairoEvent execEvent =
                new KairoEvent(
                        "evt-x",
                        Instant.now(),
                        KairoEvent.DOMAIN_EXECUTION,
                        "TOOL_INVOKED",
                        "some-string-payload",
                        Map.of());
        bus.publish(execEvent);

        assertThat(received).isEmpty();
    }

    @Test
    void toKairoEventBus_ignoresEventWithNullPayload() {
        // Edge case: payload is @Nullable in KairoEvent. null payload must be a silent drop, not
        // NPE.
        List<TeamEvent> received = new ArrayList<>();
        KairoEventBus bus = EventBusForwarders.toKairoEventBus(received::add);

        bus.publish(
                new KairoEvent(
                        "evt-null",
                        Instant.now(),
                        KairoEvent.DOMAIN_TEAM,
                        "UNKNOWN",
                        null,
                        Map.of()));

        assertThat(received).isEmpty();
    }

    @Test
    void toKairoEventBus_consumerExceptionDoesNotPropagateToPublisher() {
        // KairoEventBus contract: publish() must never throw. A misbehaving consumer must be
        // caught and logged-or-swallowed; the publisher's other subscribers still see the event.
        KairoEventBus bus =
                EventBusForwarders.toKairoEventBus(
                        te -> {
                            throw new RuntimeException("consumer blew up");
                        });

        // No exception should escape this call.
        bus.publish(wrap(teamEvent("t", "r", TeamEventType.TEAM_STARTED)));
    }

    @Test
    void toKairoEventBus_publishesAllEventsToSinkIncludingNonTeamOnes() {
        // subscribe() returns the underlying Sink's Flux — every published event flows through,
        // independent of whether the consumer side picked it up. Cache to a list to assert.
        KairoEventBus bus = EventBusForwarders.toKairoEventBus(te -> {});
        List<KairoEvent> seen = new ArrayList<>();
        bus.subscribe().subscribe(seen::add);

        KairoEvent team = wrap(teamEvent("t", "r", TeamEventType.TEAM_STARTED));
        KairoEvent exec =
                new KairoEvent("e", Instant.now(), KairoEvent.DOMAIN_EXECUTION, "X", "p", Map.of());
        bus.publish(team);
        bus.publish(exec);

        assertThat(seen).containsExactly(team, exec);
    }

    @Test
    void toKairoEventBus_subscribeWithDomainFiltersToMatchingEvents() {
        KairoEventBus bus = EventBusForwarders.toKairoEventBus(te -> {});
        List<KairoEvent> teamOnly = new ArrayList<>();
        bus.subscribe(KairoEvent.DOMAIN_TEAM).subscribe(teamOnly::add);

        KairoEvent team = wrap(teamEvent("t", "r", TeamEventType.TEAM_STARTED));
        KairoEvent exec =
                new KairoEvent("e", Instant.now(), KairoEvent.DOMAIN_EXECUTION, "X", "p", Map.of());
        bus.publish(team);
        bus.publish(exec);

        assertThat(teamOnly).containsExactly(team);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TeamEvent teamEvent(String teamId, String requestId, TeamEventType type) {
        return new TeamEvent(type, teamId, requestId, Instant.now(), Map.of());
    }

    private static KairoEvent wrap(TeamEvent te) {
        return new KairoEvent(
                "evt-" + te.requestId(),
                te.timestamp(),
                KairoEvent.DOMAIN_TEAM,
                te.type().name(),
                te,
                Map.of());
    }
}
