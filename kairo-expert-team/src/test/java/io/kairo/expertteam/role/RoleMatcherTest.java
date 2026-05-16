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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleMatcherTest {

    private ExpertRoleRegistry registry;
    private RoleMatcher matcher;

    @BeforeEach
    void setUp() {
        registry = new ExpertRoleRegistry();
        matcher = new RoleMatcher(registry);
    }

    @Test
    void matchReturnsAllRolesSorted() {
        List<RoleMatchResult> results = matcher.match("Implement a REST API endpoint");
        assertThat(results).hasSize(6); // all 6 built-in roles
        // Scores should be descending
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i - 1).score()).isGreaterThanOrEqualTo(results.get(i).score());
        }
    }

    @Test
    void coderRanksHighForImplementTask() {
        List<RoleMatchResult> results = matcher.match("Implement a new REST API endpoint");
        assertThat(results.get(0).roleId()).isEqualTo("expert:coder");
    }

    @Test
    void testerRanksHighForTestTask() {
        List<RoleMatchResult> results = matcher.match("Write unit tests for the auth module");
        assertThat(results.get(0).roleId()).isEqualTo("expert:tester");
    }

    @Test
    void reviewerRanksHighForReviewTask() {
        List<RoleMatchResult> results = matcher.match("Review the security audit code");
        assertThat(results.get(0).roleId()).isEqualTo("expert:reviewer");
    }

    @Test
    void architectRanksHighForDesignTask() {
        List<RoleMatchResult> results = matcher.match("Design the system architecture for the API");
        assertThat(results.get(0).roleId()).isEqualTo("expert:architect");
    }

    @Test
    void researcherRanksHighForInvestigateTask() {
        List<RoleMatchResult> results = matcher.match("Investigate the logging documentation");
        assertThat(results.get(0).roleId()).isEqualTo("expert:researcher");
    }

    @Test
    void selectLineupLimitsResults() {
        List<RoleMatchResult> lineup = matcher.selectLineup("Implement a feature", 3);
        assertThat(lineup.size()).isLessThanOrEqualTo(3);
        assertThat(lineup).isNotEmpty();
    }

    @Test
    void selectLineupAlwaysReturnsAtLeastOne() {
        List<RoleMatchResult> lineup = matcher.selectLineup("Something vague", 1);
        assertThat(lineup).hasSize(1);
    }

    @Test
    void selectLineupRejectsZeroMaxRoles() {
        assertThatThrownBy(() -> matcher.selectLineup("task", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchWithEmptyRegistryReturnsEmpty() {
        ExpertRoleRegistry emptyRegistry = new ExpertRoleRegistry();
        // Can't truly make it empty without removing built-ins, so test with explicit candidates
        List<RoleMatchResult> results = matcher.match("something", List.of());
        assertThat(results).isEmpty();
    }

    @Test
    void matchResultsContainProfiles() {
        List<RoleMatchResult> results = matcher.match("Implement code");
        for (RoleMatchResult r : results) {
            assertThat(r.profile()).isNotNull();
            assertThat(r.roleId()).isEqualTo(r.profile().roleId());
        }
    }
}
