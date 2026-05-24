package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EvolutionStateMachineTest {

    private final EvolutionStateMachine sm = new EvolutionStateMachine(3);

    @Test
    void idleToReviewing() {
        assertThat(sm.transit(EvolutionState.IDLE, EvolutionSignal.START_REVIEW))
                .isEqualTo(EvolutionState.REVIEWING);
    }

    @Test
    void reviewingToQuarantined() {
        assertThat(sm.transit(EvolutionState.REVIEWING, EvolutionSignal.REVIEW_COMPLETE))
                .isEqualTo(EvolutionState.QUARANTINED);
    }

    @Test
    void quarantinedToAppliedOnScanPass() {
        assertThat(sm.transit(EvolutionState.QUARANTINED, EvolutionSignal.SCAN_PASS))
                .isEqualTo(EvolutionState.APPLIED);
    }

    @Test
    void quarantinedToIdleOnScanReject() {
        assertThat(sm.transit(EvolutionState.QUARANTINED, EvolutionSignal.SCAN_REJECT))
                .isEqualTo(EvolutionState.IDLE);
    }

    @Test
    void reviewingToFailedRetryable() {
        assertThat(sm.transit(EvolutionState.REVIEWING, EvolutionSignal.FAILURE_RETRYABLE))
                .isEqualTo(EvolutionState.FAILED_RETRYABLE);
    }

    @Test
    void failedRetryableToReviewingOnRetry() {
        assertThat(sm.transit(EvolutionState.FAILED_RETRYABLE, EvolutionSignal.RETRY))
                .isEqualTo(EvolutionState.REVIEWING);
    }

    @Test
    void failedRetryableToSuspendedOnMaxRetries() {
        // FAILED_RETRYABLE + FAILURE_HARD -> SUSPENDED
        assertThat(sm.transit(EvolutionState.FAILED_RETRYABLE, EvolutionSignal.FAILURE_HARD))
                .isEqualTo(EvolutionState.SUSPENDED);
        // Also test shouldSuspend
        assertThat(sm.shouldSuspend(3)).isTrue();
        assertThat(sm.shouldSuspend(2)).isFalse();
    }

    @Test
    void suspendedToIdleOnResume() {
        assertThat(sm.transit(EvolutionState.SUSPENDED, EvolutionSignal.RESUME))
                .isEqualTo(EvolutionState.IDLE);
    }

    @Test
    void invalidTransitionThrows() {
        assertThatThrownBy(() -> sm.transit(EvolutionState.IDLE, EvolutionSignal.SCAN_PASS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid transition");
    }
}
