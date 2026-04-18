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
package io.kairo.core.skill;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class DefaultSkillRegistryTest {

    private DefaultSkillRegistry registry;
    private boolean yamlAvailable;

    private static final String VALID_SKILL_MARKDOWN =
            """
            ---
            name: remote-skill
            version: 1.0.0
            category: CODE
            triggers:
              - "review code"
              - "/review"
            ---
            # Remote Skill

            Instructions for the remote skill.
            """;

    @BeforeEach
    void setUp() {
        registry = new DefaultSkillRegistry();
        // Detect jackson-dataformat-yaml version mismatch at runtime
        try {
            new SkillMarkdownParser()
                    .parse(
                            """
                    ---
                    name: probe
                    ---
                    body
                    """);
            yamlAvailable = true;
        } catch (NoSuchMethodError | Exception e) {
            yamlAvailable = false;
        }
    }

    private SkillDefinition skill(String name, SkillCategory category) {
        return new SkillDefinition(name, "1.0.0", "desc", "instructions", List.of(), category);
    }

    // ── Basic registry operations ──

    @Test
    @DisplayName("Register and lookup skill by name")
    void testRegisterAndGet() {
        SkillDefinition skill = skill("code-review", SkillCategory.CODE);
        registry.register(skill);

        assertTrue(registry.get("code-review").isPresent());
        assertEquals("code-review", registry.get("code-review").get().name());
    }

    @Test
    @DisplayName("Get non-existent skill returns empty")
    void testGetNonExistent() {
        assertFalse(registry.get("nonexistent").isPresent());
    }

    @Test
    @DisplayName("List all registered skills")
    void testListAll() {
        registry.register(skill("skill-a", SkillCategory.CODE));
        registry.register(skill("skill-b", SkillCategory.DEVOPS));

        List<SkillDefinition> all = registry.list();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("List skills by category")
    void testListByCategory() {
        registry.register(skill("code-review", SkillCategory.CODE));
        registry.register(skill("deploy", SkillCategory.DEVOPS));
        registry.register(skill("lint", SkillCategory.CODE));

        List<SkillDefinition> codeSkills = registry.listByCategory(SkillCategory.CODE);
        assertEquals(2, codeSkills.size());
        assertTrue(codeSkills.stream().allMatch(s -> s.category() == SkillCategory.CODE));

        List<SkillDefinition> devopsSkills = registry.listByCategory(SkillCategory.DEVOPS);
        assertEquals(1, devopsSkills.size());
    }

    @Test
    @DisplayName("Duplicate skill registration replaces the old one")
    void testDuplicateRegistration() {
        registry.register(
                new SkillDefinition(
                        "dup", "1.0.0", "old", "old body", List.of(), SkillCategory.CODE));
        registry.register(
                new SkillDefinition(
                        "dup", "2.0.0", "new", "new body", List.of(), SkillCategory.CODE));

        assertEquals(1, registry.list().size());
        assertEquals("2.0.0", registry.get("dup").get().version());
    }

    @Test
    @DisplayName("Register null skill throws IllegalArgumentException")
    void testRegisterNullSkill() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
    }

    @Test
    @DisplayName("Register skill with blank name throws")
    void testRegisterBlankName() {
        SkillDefinition blank =
                new SkillDefinition("", "1.0.0", "desc", "body", List.of(), SkillCategory.CODE);
        assertThrows(IllegalArgumentException.class, () -> registry.register(blank));
    }

    @Test
    @DisplayName("Register skill with null name throws")
    void testRegisterNullName() {
        SkillDefinition noName =
                new SkillDefinition(null, "1.0.0", "desc", "body", List.of(), SkillCategory.CODE);
        assertThrows(IllegalArgumentException.class, () -> registry.register(noName));
    }

    @Test
    @DisplayName("triggerGuard() returns the configured guard")
    void testTriggerGuardAccessor() {
        assertNotNull(registry.triggerGuard());
        assertEquals(0.8f, registry.triggerGuard().confidenceThreshold(), 0.001f);
    }

    @Test
    @DisplayName("Empty registry list returns empty")
    void testEmptyRegistryList() {
        assertTrue(registry.list().isEmpty());
    }

    @Test
    @DisplayName("listByCategory on empty registry returns empty")
    void testEmptyListByCategory() {
        assertTrue(registry.listByCategory(SkillCategory.CODE).isEmpty());
    }

    // ── loadFromFile ──

    @Nested
    @DisplayName("loadFromFile")
    class LoadFromFileTests {

        @Test
        @DisplayName("reads file and delegates to parser")
        void testLoadFromFile(@TempDir Path tempDir) throws IOException {
            Assumptions.assumeTrue(
                    yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");

            Path skillFile = tempDir.resolve("skill.md");
            Files.writeString(skillFile, VALID_SKILL_MARKDOWN, StandardCharsets.UTF_8);

            StepVerifier.create(registry.loadFromFile(skillFile))
                    .assertNext(
                            skill -> {
                                assertEquals("remote-skill", skill.name());
                                assertEquals("1.0.0", skill.version());
                                assertEquals(SkillCategory.CODE, skill.category());
                                assertTrue(skill.hasInstructions());
                            })
                    .verifyComplete();

            assertTrue(registry.get("remote-skill").isPresent());
        }

        @Test
        @DisplayName("non-existent file emits error")
        void testLoadFromMissingFile(@TempDir Path tempDir) {
            Path missing = tempDir.resolve("nonexistent.md");

            StepVerifier.create(registry.loadFromFile(missing))
                    .expectError(IOException.class)
                    .verify();
        }

        @Test
        @DisplayName("malformed markdown emits error")
        void testLoadFromMalformedFile(@TempDir Path tempDir) throws IOException {
            Assumptions.assumeTrue(
                    yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");

            Path bad = tempDir.resolve("bad.md");
            Files.writeString(bad, "no front matter here", StandardCharsets.UTF_8);

            StepVerifier.create(registry.loadFromFile(bad))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
    }

    // ── loadFromUrl ──

    @Nested
    @DisplayName("loadFromUrl")
    class LoadFromUrlTests {

        @Test
        @DisplayName("loads skill from HTTP 200 response")
        void testLoadFromUrl() throws Exception {
            Assumptions.assumeTrue(
                    yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");

            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(
                        new MockResponse()
                                .setResponseCode(200)
                                .addHeader("Content-Type", "text/markdown")
                                .setBody(VALID_SKILL_MARKDOWN));
                server.start();

                String url = server.url("/skills/remote-skill.md").toString();

                StepVerifier.create(registry.loadFromUrl(url))
                        .assertNext(
                                skill -> {
                                    assertEquals("remote-skill", skill.name());
                                    assertEquals("1.0.0", skill.version());
                                    assertEquals(SkillCategory.CODE, skill.category());
                                    assertTrue(skill.hasInstructions());
                                })
                        .verifyComplete();

                assertTrue(registry.get("remote-skill").isPresent());
                assertEquals(1, registry.urlCacheSize());
            }
        }

        @Test
        @DisplayName("HTTP 404 emits IOException")
        void testLoadFromUrl404() throws Exception {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));
                server.start();

                String url = server.url("/missing.md").toString();

                StepVerifier.create(registry.loadFromUrl(url))
                        .expectErrorMatches(
                                e ->
                                        e instanceof IOException
                                                && e.getMessage().contains("HTTP 404"))
                        .verify();
            }
        }

        @Test
        @DisplayName("HTTP 500 emits IOException")
        void testLoadFromUrl500() throws Exception {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse().setResponseCode(500).setBody("Server Error"));
                server.start();

                String url = server.url("/error.md").toString();

                StepVerifier.create(registry.loadFromUrl(url))
                        .expectErrorMatches(
                                e ->
                                        e instanceof IOException
                                                && e.getMessage().contains("HTTP 500"))
                        .verify();
            }
        }

        @Test
        @DisplayName("malformed response body emits error")
        void testLoadFromUrlMalformedBody() throws Exception {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(
                        new MockResponse()
                                .setResponseCode(200)
                                .setBody("not a valid skill markdown"));
                server.start();

                String url = server.url("/bad.md").toString();

                StepVerifier.create(registry.loadFromUrl(url))
                        .expectError(IllegalArgumentException.class)
                        .verify();
            }
        }

        @Test
        @DisplayName("invalid URL emits error")
        void testLoadFromInvalidUrl() {
            StepVerifier.create(registry.loadFromUrl("not-a-url")).expectError().verify();
        }
    }

    // ── loadFromClasspath ──

    @Nested
    @DisplayName("loadFromClasspath")
    class LoadFromClasspathTests {

        @Test
        @DisplayName("loads skill from classpath resource")
        void testLoadFromClasspath() {
            Assumptions.assumeTrue(
                    yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");

            StepVerifier.create(registry.loadFromClasspath("test-skill.md"))
                    .assertNext(
                            skill -> {
                                assertEquals("test-skill", skill.name());
                                assertEquals("2.0.0", skill.version());
                                assertEquals(SkillCategory.CODE, skill.category());
                                assertTrue(skill.hasInstructions());
                                assertTrue(skill.isConditional());
                                assertTrue(skill.hasToolRestrictions());
                                assertEquals(List.of("**/*Test.java"), skill.pathPatterns());
                                assertEquals(List.of("run_tests"), skill.requiredTools());
                                assertEquals("macos", skill.platform());
                                assertEquals(5, skill.matchScore());
                                assertEquals(
                                        List.of("run_tests", "read_file"), skill.allowedTools());
                            })
                    .verifyComplete();

            assertTrue(registry.get("test-skill").isPresent());
        }

        @Test
        @DisplayName("missing classpath resource emits IOException")
        void testLoadFromClasspathMissing() {
            StepVerifier.create(registry.loadFromClasspath("nonexistent-skill.md"))
                    .expectErrorMatches(
                            e ->
                                    e instanceof IOException
                                            && e.getMessage()
                                                    .contains("Classpath resource not found"))
                    .verify();
        }
    }

    // ── TTL Cache ──

    @Nested
    @DisplayName("URL TTL Cache")
    class UrlCacheTests {

        @Test
        @DisplayName("second loadFromUrl returns cached result without HTTP call")
        void testCacheHit() throws Exception {
            Assumptions.assumeTrue(
                    yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");

            try (MockWebServer server = new MockWebServer()) {
                // Only enqueue ONE response — second call should use cache
                server.enqueue(
                        new MockResponse().setResponseCode(200).setBody(VALID_SKILL_MARKDOWN));
                server.start();

                String url = server.url("/cached-skill.md").toString();

                // First call — fetches from server
                StepVerifier.create(registry.loadFromUrl(url))
                        .assertNext(s -> assertEquals("remote-skill", s.name()))
                        .verifyComplete();

                assertEquals(1, registry.urlCacheSize());

                // Second call — should use cache (no second enqueued response)
                StepVerifier.create(registry.loadFromUrl(url))
                        .assertNext(s -> assertEquals("remote-skill", s.name()))
                        .verifyComplete();

                // Only one request was made to the server
                assertEquals(1, server.getRequestCount());
            }
        }

        @Test
        @DisplayName("cache entry expires after TTL and re-fetches")
        void testCacheExpiry() throws Exception {
            Assumptions.assumeTrue(
                    yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");

            // Use a zero-duration TTL so entries expire immediately
            DefaultSkillRegistry shortTtlRegistry =
                    new DefaultSkillRegistry(new DefaultTriggerGuard(), Duration.ZERO);

            try (MockWebServer server = new MockWebServer()) {
                // Enqueue two responses for the two fetches
                server.enqueue(
                        new MockResponse().setResponseCode(200).setBody(VALID_SKILL_MARKDOWN));
                server.enqueue(
                        new MockResponse().setResponseCode(200).setBody(VALID_SKILL_MARKDOWN));
                server.start();

                String url = server.url("/expiring-skill.md").toString();

                // First call
                StepVerifier.create(shortTtlRegistry.loadFromUrl(url))
                        .assertNext(s -> assertEquals("remote-skill", s.name()))
                        .verifyComplete();

                // Second call — TTL=0 means immediately expired, should re-fetch
                StepVerifier.create(shortTtlRegistry.loadFromUrl(url))
                        .assertNext(s -> assertEquals("remote-skill", s.name()))
                        .verifyComplete();

                // Two requests were made
                assertEquals(2, server.getRequestCount());
            }
        }

        @Test
        @DisplayName("clearUrlCache empties the cache")
        void testClearCache() throws Exception {
            Assumptions.assumeTrue(
                    yamlAvailable, "Skipping: jackson-dataformat-yaml version mismatch");

            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(
                        new MockResponse().setResponseCode(200).setBody(VALID_SKILL_MARKDOWN));
                server.enqueue(
                        new MockResponse().setResponseCode(200).setBody(VALID_SKILL_MARKDOWN));
                server.start();

                String url = server.url("/clear-test.md").toString();

                StepVerifier.create(registry.loadFromUrl(url))
                        .assertNext(s -> assertNotNull(s))
                        .verifyComplete();

                assertEquals(1, registry.urlCacheSize());
                registry.clearUrlCache();
                assertEquals(0, registry.urlCacheSize());

                // After clearing, the next call re-fetches
                StepVerifier.create(registry.loadFromUrl(url))
                        .assertNext(s -> assertNotNull(s))
                        .verifyComplete();

                assertEquals(2, server.getRequestCount());
            }
        }
    }
}
