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

import io.kairo.api.agent.Agent;
import io.kairo.api.team.Team;
import io.kairo.api.team.TeamCoordinator;
import io.kairo.expertteam.tck.NoopMessageBus;
import io.kairo.expertteam.tck.RecordingEventBus;
import io.kairo.expertteam.tck.TeamCoordinatorTCK;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

/** Drives {@link TeamCoordinatorTCK} against {@link ExpertTeamCoordinator}. */
final class ExpertTeamCoordinatorTckTest extends TeamCoordinatorTCK {

    private RecordingEventBus bus;
    private ExpertTeamCoordinator coordinator;

    @BeforeEach
    void setUp() {
        this.bus = new RecordingEventBus();
        this.coordinator = new ExpertTeamCoordinator(bus);
    }

    @Override
    protected TeamCoordinator coordinatorUnderTest() {
        return coordinator;
    }

    @Override
    protected Team happyPathTeam(Agent agent) {
        return new Team("happy-team", List.of(agent), new NoopMessageBus());
    }

    @Override
    protected Team failingTeam(Agent failingAgent) {
        return new Team("failing-team", List.of(failingAgent), new NoopMessageBus());
    }

    @Override
    protected RecordingEventBus eventBus() {
        return bus;
    }
}
