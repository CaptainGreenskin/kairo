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
package io.kairo.multiagent.team.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.TeamStep;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InternalTaskBoard} covering DAG readiness and state transitions. */
class InternalTaskBoardTest {

    private static RoleDefinition role() {
        return new RoleDefinition("r", "Role", "do", "generic", List.of());
    }

    private static TeamStep step(String id, int index, String... deps) {
        return new TeamStep(id, "step " + id, role(), List.of(deps), index);
    }

    @Test
    void readyStepsReturnsRootsInitially() {
        InternalTaskBoard board =
                new InternalTaskBoard(List.of(step("a", 0), step("b", 1, "a"), step("c", 2, "a")));

        assertThat(board.readySteps()).extracting(TeamStep::stepId).containsExactly("a");
        assertThat(board.allTerminal()).isFalse();
    }

    @Test
    void readyStepsUnblocksAfterDependencyCompleted() {
        InternalTaskBoard board =
                new InternalTaskBoard(List.of(step("a", 0), step("b", 1, "a"), step("c", 2, "a")));

        board.markInFlight("a");
        board.markCompleted("a", "artifact");

        assertThat(board.readySteps())
                .extracting(TeamStep::stepId)
                .containsExactlyInAnyOrder("b", "c");
        assertThat(board.inFlightCount()).isZero();
    }

    @Test
    void markPendingRollbackRetainsAttemptCount() {
        InternalTaskBoard board = new InternalTaskBoard(List.of(step("a", 0)));

        board.markInFlight("a");
        assertThat(board.attemptsOf("a")).isEqualTo(1);

        board.markPending("a");
        assertThat(board.stateOf("a")).isEqualTo(InternalTaskBoard.State.PENDING);

        board.markInFlight("a");
        assertThat(board.attemptsOf("a")).isEqualTo(2);
    }

    @Test
    void markSkippedTerminatesStep() {
        InternalTaskBoard board = new InternalTaskBoard(List.of(step("a", 0), step("b", 1, "a")));

        board.markInFlight("a");
        board.markSkipped("a", "retries exhausted");

        // Skipped dependency blocks descendants — they never become ready.
        assertThat(board.readySteps()).isEmpty();
        assertThat(board.allTerminal()).isFalse(); // b is still PENDING
    }

    @Test
    void allTerminalWhenEverythingCompleted() {
        InternalTaskBoard board = new InternalTaskBoard(List.of(step("a", 0), step("b", 1)));
        board.markInFlight("a");
        board.markCompleted("a", "x");
        board.markInFlight("b");
        board.markCompleted("b", "y");

        assertThat(board.allTerminal()).isTrue();
        assertThat(board.anyFailed()).isFalse();
    }

    @Test
    void anyFailedDetectsHardFailure() {
        InternalTaskBoard board = new InternalTaskBoard(List.of(step("a", 0)));
        board.markInFlight("a");
        board.markFailed("a");

        assertThat(board.anyFailed()).isTrue();
        assertThat(board.allTerminal()).isTrue();
    }

    @Test
    void validateDependenciesRejectsUnknownDep() {
        assertThatThrownBy(() -> new InternalTaskBoard(List.of(step("a", 0, "nope"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void unknownStepMutationRejected() {
        InternalTaskBoard board = new InternalTaskBoard(List.of(step("a", 0)));
        assertThatThrownBy(() -> board.markInFlight("ghost"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> board.stateOf("ghost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotReflectsCurrentState() {
        InternalTaskBoard board = new InternalTaskBoard(List.of(step("a", 0), step("b", 1, "a")));

        board.markInFlight("a");
        board.markCompleted("a", "done");

        List<InternalTaskBoard.StepRecord> snap = board.snapshot();
        assertThat(snap).hasSize(2);
        InternalTaskBoard.StepRecord aRec =
                snap.stream().filter(r -> r.step().stepId().equals("a")).findFirst().orElseThrow();
        assertThat(aRec.state()).isEqualTo(InternalTaskBoard.State.COMPLETED);
        assertThat(aRec.artifact()).isEqualTo("done");
        assertThat(aRec.attempts()).isEqualTo(1);
    }

    @Test
    void nonTerminalStepIdsTracksProgression() {
        InternalTaskBoard board = new InternalTaskBoard(List.of(step("a", 0), step("b", 1, "a")));
        assertThat(board.nonTerminalStepIds()).containsExactlyInAnyOrder("a", "b");

        board.markInFlight("a");
        board.markCompleted("a", "x");
        assertThat(board.nonTerminalStepIds()).containsExactly("b");
    }

    @Test
    void inFlightCountReflectsActiveStepsOnly() {
        InternalTaskBoard board = new InternalTaskBoard(List.of(step("a", 0), step("b", 1)));

        assertThat(board.inFlightCount()).isZero();
        board.markInFlight("a");
        assertThat(board.inFlightCount()).isEqualTo(1);
        board.markInFlight("b");
        assertThat(board.inFlightCount()).isEqualTo(2);
        board.markCompleted("a", "x");
        assertThat(board.inFlightCount()).isEqualTo(1);
    }
}
