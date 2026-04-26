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

import io.kairo.expertteam.ExpertTeamStateMachine.State;
import org.junit.jupiter.api.Test;

final class ExpertTeamStateMachineTest {

    private final ExpertTeamStateMachine sm = new ExpertTeamStateMachine();

    @Test
    void idleOnlyGoesToPlanning() {
        assertThat(sm.canTransition(State.IDLE, State.PLANNING)).isTrue();
        assertThat(sm.canTransition(State.IDLE, State.GENERATING)).isFalse();
        assertThat(sm.canTransition(State.IDLE, State.COMPLETED)).isFalse();
    }

    @Test
    void planningGoesToGeneratingOrFailed() {
        assertThat(sm.canTransition(State.PLANNING, State.GENERATING)).isTrue();
        assertThat(sm.canTransition(State.PLANNING, State.FAILED)).isTrue();
        assertThat(sm.canTransition(State.PLANNING, State.TIMEOUT)).isFalse();
    }

    @Test
    void evaluatingCanLoopBackToGeneratingForRevise() {
        assertThat(sm.canTransition(State.EVALUATING, State.GENERATING)).isTrue();
        assertThat(sm.canTransition(State.EVALUATING, State.COMPLETED)).isTrue();
        assertThat(sm.canTransition(State.EVALUATING, State.DEGRADED)).isTrue();
        assertThat(sm.canTransition(State.EVALUATING, State.FAILED)).isTrue();
        assertThat(sm.canTransition(State.EVALUATING, State.TIMEOUT)).isTrue();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (State terminal :
                new State[] {State.COMPLETED, State.FAILED, State.DEGRADED, State.TIMEOUT}) {
            assertThat(sm.isTerminal(terminal)).as("%s must be terminal", terminal).isTrue();
            for (State target : State.values()) {
                assertThat(sm.canTransition(terminal, target))
                        .as("%s -> %s must be rejected", terminal, target)
                        .isFalse();
            }
        }
    }

    @Test
    void transitionThrowsOnIllegalMove() {
        assertThatThrownBy(() -> sm.transition(State.IDLE, State.COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDLE -> COMPLETED");
    }

    @Test
    void transitionReturnsTargetStateOnLegalMove() {
        assertThat(sm.transition(State.IDLE, State.PLANNING)).isEqualTo(State.PLANNING);
        assertThat(sm.transition(State.PLANNING, State.GENERATING)).isEqualTo(State.GENERATING);
    }
}
