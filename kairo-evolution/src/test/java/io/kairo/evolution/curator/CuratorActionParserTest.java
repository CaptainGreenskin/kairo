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

import java.util.List;
import org.junit.jupiter.api.Test;

class CuratorActionParserTest {

    @Test
    void parsesAllFiveActionKinds() {
        String json =
                """
                [
                  {"action": "merge_into_umbrella",
                   "umbrella": "pr-triage",
                   "siblings": ["pr-fix-7", "pr-fix-8"],
                   "rationale": "siblings overlap"},
                  {"action": "create_umbrella",
                   "umbrella": "release-prep",
                   "siblings": ["release-rc1", "release-rc2"],
                   "skill_body": "# Release prep\\n\\nWorkflow...",
                   "description": "Release prep umbrella",
                   "rationale": "no existing broad skill"},
                  {"action": "demote",
                   "umbrella": "deploys",
                   "sibling": "gcp-quirks",
                   "support_kind": "references",
                   "file_name": "gcp-quirks.md",
                   "body": "Notes on GCP",
                   "rationale": "narrow-but-valuable"},
                  {"action": "keep",
                   "umbrella": "agent-runtime",
                   "rationale": "already class-level"},
                  {"action": "archive",
                   "umbrella": "stale-session-23",
                   "rationale": "no longer relevant"}
                ]
                """;

        List<CuratorAction> actions = CuratorActionParser.parse(json);

        assertThat(actions).hasSize(5);
        assertThat(actions.get(0)).isInstanceOf(CuratorAction.MergeIntoUmbrella.class);
        assertThat(actions.get(1)).isInstanceOf(CuratorAction.CreateUmbrella.class);
        assertThat(actions.get(2)).isInstanceOf(CuratorAction.DemoteToSupport.class);
        assertThat(actions.get(3)).isInstanceOf(CuratorAction.Keep.class);
        assertThat(actions.get(4)).isInstanceOf(CuratorAction.Archive.class);

        CuratorAction.MergeIntoUmbrella m = (CuratorAction.MergeIntoUmbrella) actions.get(0);
        assertThat(m.siblings()).containsExactly("pr-fix-7", "pr-fix-8");

        CuratorAction.DemoteToSupport d = (CuratorAction.DemoteToSupport) actions.get(2);
        assertThat(d.supportKind()).isEqualTo(CuratorAction.SupportKind.REFERENCES);
        assertThat(d.fileName()).isEqualTo("gcp-quirks.md");
    }

    @Test
    void stripsFencedCodeBlocks() {
        String json =
                """
                ```json
                [{"action": "keep", "umbrella": "foo", "rationale": "ok"}]
                ```
                """;
        List<CuratorAction> actions = CuratorActionParser.parse(json);
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0)).isInstanceOf(CuratorAction.Keep.class);
    }

    @Test
    void emptyArrayReturnsEmptyList() {
        assertThat(CuratorActionParser.parse("[]")).isEmpty();
    }

    @Test
    void blankOrNullReturnsEmptyList() {
        assertThat(CuratorActionParser.parse(null)).isEmpty();
        assertThat(CuratorActionParser.parse("")).isEmpty();
        assertThat(CuratorActionParser.parse("   ")).isEmpty();
    }

    @Test
    void malformedJsonReturnsEmptyList() {
        assertThat(CuratorActionParser.parse("not json")).isEmpty();
        assertThat(CuratorActionParser.parse("[{broken")).isEmpty();
    }

    @Test
    void unknownActionTypeIsSkipped() {
        String json =
                """
                [{"action": "do-something-weird", "umbrella": "x"},
                 {"action": "keep", "umbrella": "y", "rationale": "ok"}]
                """;
        List<CuratorAction> actions = CuratorActionParser.parse(json);
        assertThat(actions).hasSize(1);
    }

    @Test
    void missingUmbrellaFieldIsSkipped() {
        String json =
                """
                [{"action": "keep", "rationale": "no umbrella name"}]
                """;
        assertThat(CuratorActionParser.parse(json)).isEmpty();
    }
}
