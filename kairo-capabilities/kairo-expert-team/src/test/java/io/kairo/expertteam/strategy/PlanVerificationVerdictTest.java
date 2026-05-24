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
package io.kairo.expertteam.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.expertteam.strategy.PlanVerificationVerdict.VerificationOutcome;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PlanVerificationVerdictTest {

    @Test
    void verifiedFactoryHasEmptyIssues() {
        PlanVerificationVerdict v = PlanVerificationVerdict.verified("all good");

        assertThat(v.outcome()).isEqualTo(VerificationOutcome.VERIFIED);
        assertThat(v.reason()).isEqualTo("all good");
        assertThat(v.issues()).isEmpty();
        assertThat(v.isSuccess()).isTrue();
    }

    @Test
    void partialFactory() {
        PlanVerificationVerdict v =
                PlanVerificationVerdict.partial("some warnings", List.of("issue1"));

        assertThat(v.outcome()).isEqualTo(VerificationOutcome.PARTIAL);
        assertThat(v.isSuccess()).isFalse();
        assertThat(v.issues()).containsExactly("issue1");
    }

    @Test
    void failedFactory() {
        PlanVerificationVerdict v =
                PlanVerificationVerdict.failed("broken", List.of("issue1", "issue2"));

        assertThat(v.outcome()).isEqualTo(VerificationOutcome.FAILED);
        assertThat(v.isSuccess()).isFalse();
        assertThat(v.issues()).hasSize(2);
    }

    @Test
    void nullOutcomeThrows() {
        assertThatThrownBy(() -> new PlanVerificationVerdict(null, "reason", List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullReasonThrows() {
        assertThatThrownBy(
                        () ->
                                new PlanVerificationVerdict(
                                        VerificationOutcome.VERIFIED, null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullIssuesDefaultsToEmptyList() {
        PlanVerificationVerdict v =
                new PlanVerificationVerdict(VerificationOutcome.VERIFIED, "ok", null);
        assertThat(v.issues()).isEmpty();
    }

    @Test
    void issuesListIsDefensivelyCopied() {
        ArrayList<String> mutable = new ArrayList<>();
        mutable.add("issue1");

        PlanVerificationVerdict v =
                new PlanVerificationVerdict(VerificationOutcome.FAILED, "bad", mutable);
        mutable.add("issue2");

        assertThat(v.issues()).hasSize(1);
    }
}
