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
package io.kairo.core.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.a2a.AgentCard;
import io.kairo.api.a2a.AgentSkill;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link InProcessAgentCardResolver}. */
class InProcessAgentCardResolverTest {

    private InProcessAgentCardResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new InProcessAgentCardResolver();
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("register and resolve by id")
        void registerAndResolve() {
            AgentCard card = AgentCard.of("agent-1", "Test Agent", "A test agent");
            resolver.register(card);

            assertThat(resolver.resolve("agent-1")).containsSame(card);
        }

        @Test
        @DisplayName("resolve unknown id returns empty")
        void resolveUnknownReturnsEmpty() {
            assertThat(resolver.resolve("unknown")).isEmpty();
        }

        @Test
        @DisplayName("register null card throws NPE")
        void registerNullThrows() {
            assertThatThrownBy(() -> resolver.register(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("register overwrites existing card with same id")
        void registerOverwrites() {
            resolver.register(AgentCard.of("agent-1", "V1", "First"));
            resolver.register(AgentCard.of("agent-1", "V2", "Second"));

            assertThat(resolver.resolve("agent-1"))
                    .hasValueSatisfying(c -> assertThat(c.name()).isEqualTo("V2"));
        }
    }

    @Nested
    @DisplayName("Discovery by tags")
    class TagDiscovery {

        @Test
        @DisplayName("discover with AND semantics — all tags required")
        void discoverAndSemantics() {
            resolver.register(
                    new AgentCard(
                            "a",
                            "Java Reviewer",
                            "desc",
                            "1.0",
                            List.of("java", "code-review"),
                            false,
                            List.of(),
                            null));
            resolver.register(
                    new AgentCard(
                            "b",
                            "Python Reviewer",
                            "desc",
                            "1.0",
                            List.of("python", "code-review"),
                            false,
                            List.of(),
                            null));
            resolver.register(
                    new AgentCard(
                            "c",
                            "Java Builder",
                            "desc",
                            "1.0",
                            List.of("java", "build"),
                            false,
                            List.of(),
                            null));

            List<AgentCard> result = resolver.discover(Set.of("java", "code-review"));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("a");
        }

        @Test
        @DisplayName("discover with single tag")
        void discoverSingleTag() {
            resolver.register(
                    new AgentCard(
                            "a",
                            "Agent A",
                            "desc",
                            "1.0",
                            List.of("java"),
                            false,
                            List.of(),
                            null));
            resolver.register(
                    new AgentCard(
                            "b",
                            "Agent B",
                            "desc",
                            "1.0",
                            List.of("python"),
                            false,
                            List.of(),
                            null));

            List<AgentCard> result = resolver.discover(Set.of("java"));
            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("a");
        }

        @Test
        @DisplayName("discover with no matching tags returns empty")
        void discoverNoMatch() {
            resolver.register(
                    new AgentCard(
                            "a",
                            "Agent A",
                            "desc",
                            "1.0",
                            List.of("java"),
                            false,
                            List.of(),
                            null));

            assertThat(resolver.discover(Set.of("rust"))).isEmpty();
        }

        @Test
        @DisplayName("discover with empty tags returns all agents")
        void discoverEmptyTagsReturnsAll() {
            resolver.register(AgentCard.of("a", "A", "desc"));
            resolver.register(AgentCard.of("b", "B", "desc"));

            assertThat(resolver.discover(Set.of())).hasSize(2);
        }

        @Test
        @DisplayName("discover with null tags returns all agents")
        void discoverNullTagsReturnsAll() {
            resolver.register(AgentCard.of("a", "A", "desc"));

            assertThat(resolver.discover(null)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("List and unregister")
    class ListUnregister {

        @Test
        @DisplayName("listAll returns all registered cards")
        void listAll() {
            resolver.register(AgentCard.of("a", "A", "desc"));
            resolver.register(AgentCard.of("b", "B", "desc"));

            assertThat(resolver.listAll()).hasSize(2);
        }

        @Test
        @DisplayName("listAll returns empty when no cards registered")
        void listAllEmpty() {
            assertThat(resolver.listAll()).isEmpty();
        }

        @Test
        @DisplayName("unregister removes card")
        void unregister() {
            resolver.register(AgentCard.of("a", "A", "desc"));
            resolver.unregister("a");

            assertThat(resolver.resolve("a")).isEmpty();
        }

        @Test
        @DisplayName("unregister unknown id is a no-op")
        void unregisterUnknown() {
            resolver.unregister("nonexistent");
            assertThat(resolver.listAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("AgentCard with skills")
    class Skills {

        @Test
        @DisplayName("card with skills resolves correctly")
        void cardWithSkills() {
            AgentSkill skill =
                    new AgentSkill(
                            "code-review",
                            "Code Review",
                            "Reviews code",
                            List.of("review", "java"));
            AgentCard card =
                    new AgentCard(
                            "reviewer",
                            "Reviewer",
                            "desc",
                            "1.0",
                            List.of("review"),
                            true,
                            List.of(skill),
                            null);

            resolver.register(card);
            assertThat(resolver.resolve("reviewer"))
                    .hasValueSatisfying(
                            c -> {
                                assertThat(c.skills()).hasSize(1);
                                assertThat(c.skills().get(0).id()).isEqualTo("code-review");
                                assertThat(c.streaming()).isTrue();
                            });
        }
    }
}
