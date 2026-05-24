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
package io.kairo.evolution.curator;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillTrustLevel;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrefixClusterCuratorTest {

    private static EvolvedSkill skill(String name, String body) {
        Instant now = Instant.now();
        return new EvolvedSkill(
                name,
                "1.0",
                "",
                body,
                "general",
                Set.of(),
                SkillTrustLevel.DRAFT,
                null,
                now,
                now,
                0L);
    }

    private static SkillCatalog cat(EvolvedSkill... candidates) {
        return new SkillCatalog(
                java.util.Arrays.stream(candidates)
                        .map(s -> new SkillCatalog.Entry(s, null))
                        .toList(),
                List.of());
    }

    @Test
    void emitsMergeActionForClusterOfTwoOrMore() {
        SkillCatalog catalog =
                cat(
                        skill("pr-triage", "big umbrella body" + "x".repeat(500)),
                        skill("pr-fix-7", "short"),
                        skill("pr-fix-8", "short"));

        List<CuratorAction> actions = new PrefixClusterCurator().propose(catalog).block();

        assertThat(actions).hasSize(1);
        CuratorAction.MergeIntoUmbrella merge = (CuratorAction.MergeIntoUmbrella) actions.get(0);
        assertThat(merge.umbrella()).isEqualTo("pr-triage");
        assertThat(merge.siblings()).containsExactlyInAnyOrder("pr-fix-7", "pr-fix-8");
    }

    @Test
    void singletonClustersAreIgnored() {
        SkillCatalog catalog = cat(skill("pr-only", "short"), skill("docs-only", "short"));

        List<CuratorAction> actions = new PrefixClusterCurator().propose(catalog).block();

        assertThat(actions).isEmpty();
    }

    @Test
    void emptyCatalogReturnsEmptyActions() {
        List<CuratorAction> actions = new PrefixClusterCurator().propose(cat()).block();
        assertThat(actions).isEmpty();
    }

    @Test
    void minClusterSizeIsRespected() {
        SkillCatalog catalog = cat(skill("pr-a", "x"), skill("pr-b", "x"));

        List<CuratorAction> two = new PrefixClusterCurator(2).propose(catalog).block();
        List<CuratorAction> three = new PrefixClusterCurator(3).propose(catalog).block();

        assertThat(two).hasSize(1);
        assertThat(three).isEmpty();
    }

    @Test
    void picksLongestBodyAsUmbrella() {
        SkillCatalog catalog =
                cat(
                        skill("pr-a", "short"),
                        skill("pr-b", "x".repeat(1000)),
                        skill("pr-c", "medium body"));
        CuratorAction.MergeIntoUmbrella merge =
                (CuratorAction.MergeIntoUmbrella)
                        new PrefixClusterCurator().propose(catalog).block().get(0);
        assertThat(merge.umbrella()).isEqualTo("pr-b");
        assertThat(merge.siblings()).containsExactlyInAnyOrder("pr-a", "pr-c");
    }

    @Test
    void firstSegmentDetectionHandlesUnderscoresAndDashes() {
        assertThat(PrefixClusterCurator.firstWordOrSegment("pr-foo-bar")).isEqualTo("pr");
        assertThat(PrefixClusterCurator.firstWordOrSegment("pr_foo")).isEqualTo("pr");
        assertThat(PrefixClusterCurator.firstWordOrSegment("monolith")).isEqualTo("monolith");
        assertThat(PrefixClusterCurator.firstWordOrSegment("")).isEmpty();
    }
}
