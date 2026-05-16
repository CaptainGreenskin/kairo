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
package io.kairo.expertteam.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleCapabilitiesTest {

    @Test
    void perfectMatchScoresHigh() {
        RoleCapabilities caps =
                new RoleCapabilities(
                        Set.of("java"),
                        Set.of("spring"),
                        Set.of("backend", "api"),
                        Set.of("implement"));
        TaskFeatures features =
                new TaskFeatures(
                        Set.of("java"),
                        Set.of("spring"),
                        Set.of("backend", "api"),
                        Set.of("implement"));

        double score = caps.score(features);
        assertThat(score).isCloseTo(1.0, within(0.001));
    }

    @Test
    void noOverlapScoresLow() {
        RoleCapabilities caps =
                new RoleCapabilities(
                        Set.of("python"), Set.of("django"), Set.of("frontend"), Set.of("review"));
        TaskFeatures features =
                new TaskFeatures(
                        Set.of("java"), Set.of("spring"), Set.of("backend"), Set.of("implement"));

        double score = caps.score(features);
        assertThat(score).isCloseTo(0.0, within(0.001));
    }

    @Test
    void emptyCapabilitiesGetPartialCredit() {
        RoleCapabilities caps = RoleCapabilities.EMPTY;
        TaskFeatures features =
                new TaskFeatures(
                        Set.of("java"), Set.of("spring"), Set.of("backend"), Set.of("implement"));

        double score = caps.score(features);
        assertThat(score).isCloseTo(0.5, within(0.001));
    }

    @Test
    void emptyFeaturesReturnNeutral() {
        RoleCapabilities caps =
                new RoleCapabilities(
                        Set.of("java"), Set.of("spring"), Set.of("backend"), Set.of("implement"));
        TaskFeatures features = new TaskFeatures(Set.of(), Set.of(), Set.of(), Set.of());

        double score = caps.score(features);
        assertThat(score).isCloseTo(0.5, within(0.001));
    }

    @Test
    void partialOverlapScoresBetween() {
        RoleCapabilities caps =
                new RoleCapabilities(
                        Set.of("java"), Set.of(), Set.of("backend", "api"), Set.of("implement"));
        TaskFeatures features =
                new TaskFeatures(
                        Set.of("java", "python"),
                        Set.of("spring"),
                        Set.of("backend"),
                        Set.of("implement", "test"));

        double score = caps.score(features);
        assertThat(score).isBetween(0.3, 0.8);
    }

    @Test
    void actionWeighsMostHeavily() {
        RoleCapabilities matchAction =
                new RoleCapabilities(Set.of(), Set.of(), Set.of(), Set.of("implement"));
        RoleCapabilities matchDomain =
                new RoleCapabilities(Set.of(), Set.of(), Set.of("backend"), Set.of());

        TaskFeatures features =
                new TaskFeatures(Set.of(), Set.of(), Set.of("backend"), Set.of("implement"));

        double actionScore = matchAction.score(features);
        double domainScore = matchDomain.score(features);

        // Action has 0.4 weight vs domain 0.3 — matching action alone should score higher
        assertThat(actionScore).isGreaterThan(domainScore);
    }

    @Test
    void nullFieldsDefaultToEmpty() {
        RoleCapabilities caps = new RoleCapabilities(null, null, null, null);
        assertThat(caps.languages()).isEmpty();
        assertThat(caps.frameworks()).isEmpty();
        assertThat(caps.domains()).isEmpty();
        assertThat(caps.actions()).isEmpty();
    }
}
