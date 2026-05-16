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
package io.kairo.core.hook;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.hook.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ExternalHookInfrastructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DefaultHookChain chain;

    @BeforeEach
    void setUp() {
        chain = new DefaultHookChain();
    }

    // ── Generic firePhase dispatch ─────────────────────────────────────────

    @Nested
    @DisplayName("firePhase generic dispatch")
    class FirePhaseTest {

        static class UserPromptListener {
            final AtomicReference<String> captured = new AtomicReference<>();

            @HookHandler(HookPhase.USER_PROMPT_SUBMIT)
            public UserPromptSubmitEvent onSubmit(UserPromptSubmitEvent event) {
                captured.set(event.prompt());
                return event;
            }
        }

        @Test
        @DisplayName("firePhase dispatches to @HookHandler(USER_PROMPT_SUBMIT)")
        void firesInProcessHandler() {
            UserPromptListener listener = new UserPromptListener();
            chain.register(listener);

            UserPromptSubmitEvent event = new UserPromptSubmitEvent("s1", "hello", "/tmp");

            StepVerifier.create(chain.firePhase(HookPhase.USER_PROMPT_SUBMIT, event))
                    .expectNext(event)
                    .verifyComplete();

            assertEquals("hello", listener.captured.get());
        }

        @Test
        @DisplayName("firePhase passes through when no handlers registered")
        void passesThrough() {
            SetupEvent event = new SetupEvent("s1", "init");

            StepVerifier.create(chain.firePhase(HookPhase.SETUP, event))
                    .expectNext(event)
                    .verifyComplete();
        }

        static class AbortingListener {
            @HookHandler(HookPhase.PERMISSION_REQUEST)
            public HookResult<PermissionRequestEvent> onPermission(PermissionRequestEvent event) {
                return HookResult.abort(event, "denied by policy");
            }
        }

        @Test
        @DisplayName("firePhaseWithResult returns ABORT from handler")
        void abortFromHandler() {
            chain.register(new AbortingListener());

            PermissionRequestEvent event =
                    new PermissionRequestEvent("s1", "Bash", Map.of("cmd", "rm -rf"), "dangerous");

            StepVerifier.create(chain.firePhaseWithResult(HookPhase.PERMISSION_REQUEST, event))
                    .assertNext(
                            result -> {
                                assertEquals(HookResult.Decision.ABORT, result.decision());
                                assertEquals("denied by policy", result.reason());
                            })
                    .verifyComplete();
        }
    }

    // ── External hook binding + execution ──────────────────────────────────

    @Nested
    @DisplayName("External hook execution")
    class ExternalHookTest {

        @Test
        @DisplayName("registered external binding fires for matching phase")
        void externalBindingFires() {
            AtomicReference<HookEvent> captured = new AtomicReference<>();

            ExternalHookExecutor mockExecutor =
                    new ExternalHookExecutor() {
                        @Override
                        public String type() {
                            return "test";
                        }

                        @Override
                        @SuppressWarnings("unchecked")
                        public <T extends HookEvent> Mono<HookResult<T>> execute(
                                T event, ExternalHookConfig config) {
                            captured.set(event);
                            return Mono.just(HookResult.proceed(event));
                        }
                    };

            chain.registerExecutor(mockExecutor);
            chain.registerExternalBinding(
                    new ExternalHookBinding(
                            HookPhase.SETUP,
                            new ExternalHookConfig(
                                    "test", null, null, Map.of(), List.of(), null, null, null)));

            SetupEvent event = new SetupEvent("s1", "init");

            StepVerifier.create(chain.firePhase(HookPhase.SETUP, event))
                    .expectNext(event)
                    .verifyComplete();

            assertNotNull(captured.get());
        }

        @Test
        @DisplayName("external binding does NOT fire for non-matching phase")
        void noFireForWrongPhase() {
            AtomicReference<HookEvent> captured = new AtomicReference<>();

            ExternalHookExecutor mockExecutor =
                    new ExternalHookExecutor() {
                        @Override
                        public String type() {
                            return "test";
                        }

                        @Override
                        public <T extends HookEvent> Mono<HookResult<T>> execute(
                                T event, ExternalHookConfig config) {
                            captured.set(event);
                            return Mono.just(HookResult.proceed(event));
                        }
                    };

            chain.registerExecutor(mockExecutor);
            chain.registerExternalBinding(
                    new ExternalHookBinding(
                            HookPhase.PRE_ACTING,
                            new ExternalHookConfig(
                                    "test", null, null, Map.of(), List.of(), null, null, null)));

            SetupEvent event = new SetupEvent("s1", "init");
            chain.firePhase(HookPhase.SETUP, event).block();

            assertNull(captured.get());
        }

        @Test
        @DisplayName("clearExternalBindings removes all bindings")
        void clearBindings() {
            AtomicReference<HookEvent> captured = new AtomicReference<>();

            ExternalHookExecutor mockExecutor =
                    new ExternalHookExecutor() {
                        @Override
                        public String type() {
                            return "test";
                        }

                        @Override
                        public <T extends HookEvent> Mono<HookResult<T>> execute(
                                T event, ExternalHookConfig config) {
                            captured.set(event);
                            return Mono.just(HookResult.proceed(event));
                        }
                    };

            chain.registerExecutor(mockExecutor);
            chain.registerExternalBinding(
                    new ExternalHookBinding(
                            HookPhase.SETUP,
                            new ExternalHookConfig(
                                    "test", null, null, Map.of(), List.of(), null, null, null)));

            chain.clearExternalBindings();

            SetupEvent event = new SetupEvent("s1", "init");
            chain.firePhase(HookPhase.SETUP, event).block();

            assertNull(captured.get());
        }
    }

    // ── Matcher filtering ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Matcher filtering")
    class MatcherTest {

        @Test
        @DisplayName("pipe-separated literal match")
        void literalMatch() {
            assertTrue(DefaultHookChain.matchesMatcher("Bash|Read", "Bash"));
            assertTrue(DefaultHookChain.matchesMatcher("Bash|Read", "Read"));
            assertFalse(DefaultHookChain.matchesMatcher("Bash|Read", "Write"));
        }

        @Test
        @DisplayName("regex match")
        void regexMatch() {
            assertTrue(DefaultHookChain.matchesMatcher("Bash.*", "BashTool"));
            assertTrue(DefaultHookChain.matchesMatcher(".*Tool", "BashTool"));
        }

        @Test
        @DisplayName("null or empty target matches everything")
        void nullTargetMatchesAll() {
            assertTrue(DefaultHookChain.matchesMatcher("Bash", null));
            assertTrue(DefaultHookChain.matchesMatcher("Bash", ""));
        }

        @Test
        @DisplayName("matcher filters external bindings by tool name")
        void matcherFiltersBindings() {
            AtomicReference<HookEvent> captured = new AtomicReference<>();

            ExternalHookExecutor mockExecutor =
                    new ExternalHookExecutor() {
                        @Override
                        public String type() {
                            return "test";
                        }

                        @Override
                        public <T extends HookEvent> Mono<HookResult<T>> execute(
                                T event, ExternalHookConfig config) {
                            captured.set(event);
                            return Mono.just(HookResult.proceed(event));
                        }
                    };

            chain.registerExecutor(mockExecutor);
            chain.registerExternalBinding(
                    new ExternalHookBinding(
                            HookPhase.PRE_ACTING,
                            new ExternalHookConfig(
                                    "test",
                                    null,
                                    null,
                                    Map.of(),
                                    List.of(),
                                    null,
                                    "Bash|Read",
                                    null)));

            PreActingEvent bashEvent = new PreActingEvent("Bash", Map.of(), false);
            chain.firePhase(HookPhase.PRE_ACTING, bashEvent).block();
            assertNotNull(captured.get(), "should fire for Bash");

            captured.set(null);
            PreActingEvent writeEvent = new PreActingEvent("Write", Map.of(), false);
            chain.firePhase(HookPhase.PRE_ACTING, writeEvent).block();
            assertNull(captured.get(), "should NOT fire for Write");
        }
    }

    // ── CommandHookExecutor ────────────────────────────────────────────────

    @Nested
    @DisplayName("CommandHookExecutor")
    class CommandHookExecutorTest {

        @Test
        @DisplayName("exit code 0 with JSON stdout returns parsed decision")
        void exitZeroWithJson() {
            CommandHookExecutor executor = new CommandHookExecutor(objectMapper);
            UserPromptSubmitEvent event = new UserPromptSubmitEvent("s1", "hello", "/tmp");

            ExternalHookConfig config =
                    new ExternalHookConfig(
                            "command",
                            "echo '{\"decision\":\"CONTINUE\",\"reason\":\"ok\"}'",
                            null,
                            Map.of(),
                            List.of(),
                            Duration.ofSeconds(5),
                            null,
                            null);

            HookResult<UserPromptSubmitEvent> result =
                    executor.<UserPromptSubmitEvent>execute(event, config).block();

            assertNotNull(result);
            assertEquals(HookResult.Decision.CONTINUE, result.decision());
            assertEquals("ok", result.reason());
        }

        @Test
        @DisplayName("exit code 2 returns ABORT")
        void exitTwoAborts() {
            CommandHookExecutor executor = new CommandHookExecutor(objectMapper);
            UserPromptSubmitEvent event = new UserPromptSubmitEvent("s1", "hello", "/tmp");

            ExternalHookConfig config =
                    new ExternalHookConfig(
                            "command",
                            "echo '{\"reason\":\"blocked\"}'; exit 2",
                            null,
                            Map.of(),
                            List.of(),
                            Duration.ofSeconds(5),
                            null,
                            null);

            HookResult<UserPromptSubmitEvent> result =
                    executor.<UserPromptSubmitEvent>execute(event, config).block();

            assertNotNull(result);
            assertEquals(HookResult.Decision.ABORT, result.decision());
            assertEquals("blocked", result.reason());
        }

        @Test
        @DisplayName("empty stdout returns CONTINUE")
        void emptyStdout() {
            CommandHookExecutor executor = new CommandHookExecutor(objectMapper);
            UserPromptSubmitEvent event = new UserPromptSubmitEvent("s1", "hello", "/tmp");

            ExternalHookConfig config =
                    new ExternalHookConfig(
                            "command",
                            "true",
                            null,
                            Map.of(),
                            List.of(),
                            Duration.ofSeconds(5),
                            null,
                            null);

            HookResult<UserPromptSubmitEvent> result =
                    executor.<UserPromptSubmitEvent>execute(event, config).block();

            assertNotNull(result);
            assertEquals(HookResult.Decision.CONTINUE, result.decision());
        }

        @Test
        @DisplayName("blank command returns CONTINUE without executing")
        void blankCommand() {
            CommandHookExecutor executor = new CommandHookExecutor(objectMapper);
            UserPromptSubmitEvent event = new UserPromptSubmitEvent("s1", "hello", "/tmp");

            ExternalHookConfig config =
                    new ExternalHookConfig(
                            "command",
                            "",
                            null,
                            Map.of(),
                            List.of(),
                            Duration.ofSeconds(5),
                            null,
                            null);

            HookResult<UserPromptSubmitEvent> result =
                    executor.<UserPromptSubmitEvent>execute(event, config).block();

            assertNotNull(result);
            assertEquals(HookResult.Decision.CONTINUE, result.decision());
        }

        @Test
        @DisplayName("timeout returns CONTINUE")
        void timeout() {
            CommandHookExecutor executor = new CommandHookExecutor(objectMapper);
            UserPromptSubmitEvent event = new UserPromptSubmitEvent("s1", "hello", "/tmp");

            ExternalHookConfig config =
                    new ExternalHookConfig(
                            "command",
                            "sleep 30",
                            null,
                            Map.of(),
                            List.of(),
                            Duration.ofSeconds(1),
                            null,
                            null);

            HookResult<UserPromptSubmitEvent> result =
                    executor.<UserPromptSubmitEvent>execute(event, config).block();

            assertNotNull(result);
            assertEquals(HookResult.Decision.CONTINUE, result.decision());
        }
    }

    // ── HttpHookExecutor env var interpolation ─────────────────────────────

    @Nested
    @DisplayName("HttpHookExecutor env var interpolation")
    class HttpHookEnvVarTest {

        @Test
        @DisplayName("allowed env vars are interpolated")
        void interpolatesAllowed() {
            ExternalHookConfig config =
                    new ExternalHookConfig(
                            "http",
                            null,
                            "http://example.com",
                            Map.of(),
                            List.of("HOME"),
                            null,
                            null,
                            null);

            String home = System.getenv("HOME");
            if (home != null) {
                String result = HttpHookExecutor.interpolateEnvVars("path=$HOME/dir", config);
                assertEquals("path=" + home + "/dir", result);
            }
        }

        @Test
        @DisplayName("non-allowed env vars are NOT interpolated")
        void doesNotInterpolateDisallowed() {
            ExternalHookConfig config =
                    new ExternalHookConfig(
                            "http",
                            null,
                            "http://example.com",
                            Map.of(),
                            List.of(),
                            null,
                            null,
                            null);

            String result = HttpHookExecutor.interpolateEnvVars("token=$SECRET_KEY", config);
            assertEquals("token=$SECRET_KEY", result);
        }

        @Test
        @DisplayName("null value passes through")
        void nullValue() {
            ExternalHookConfig config =
                    new ExternalHookConfig(
                            "http",
                            null,
                            "http://example.com",
                            Map.of(),
                            List.of(),
                            null,
                            null,
                            null);

            assertNull(HttpHookExecutor.interpolateEnvVars(null, config));
        }
    }

    // ── FileBasedHookRegistry ──────────────────────────────────────────────

    @Nested
    @DisplayName("FileBasedHookRegistry")
    class FileBasedHookRegistryTest {

        @TempDir Path tempDir;

        @Test
        @DisplayName("loads bindings from valid config file")
        void loadsValidConfig() throws IOException {
            Path configFile = tempDir.resolve("hooks.json");
            Files.writeString(
                    configFile,
                    """
                    {
                      "hooks": {
                        "PRE_ACTING": [
                          { "type": "command", "command": "echo ok", "matcher": "Bash|Read" }
                        ],
                        "SETUP": [
                          { "type": "command", "command": "init.sh" }
                        ]
                      }
                    }
                    """);

            FileBasedHookRegistry registry =
                    new FileBasedHookRegistry(objectMapper, List.of(configFile));

            List<ExternalHookBinding> bindings = registry.loadAll();
            assertEquals(2, bindings.size());

            ExternalHookBinding preActing = bindings.get(0);
            assertEquals(HookPhase.PRE_ACTING, preActing.phase());
            assertEquals("command", preActing.config().type());
            assertEquals("echo ok", preActing.config().command());
            assertEquals("Bash|Read", preActing.config().matcher());

            ExternalHookBinding setup = bindings.get(1);
            assertEquals(HookPhase.SETUP, setup.phase());
        }

        @Test
        @DisplayName("skips unknown phase names with warning")
        void skipsUnknownPhase() throws IOException {
            Path configFile = tempDir.resolve("hooks.json");
            Files.writeString(
                    configFile,
                    """
                    {
                      "hooks": {
                        "UNKNOWN_PHASE": [
                          { "type": "command", "command": "echo" }
                        ],
                        "SETUP": [
                          { "type": "command", "command": "init.sh" }
                        ]
                      }
                    }
                    """);

            FileBasedHookRegistry registry =
                    new FileBasedHookRegistry(objectMapper, List.of(configFile));

            List<ExternalHookBinding> bindings = registry.loadAll();
            assertEquals(1, bindings.size());
            assertEquals(HookPhase.SETUP, bindings.get(0).phase());
        }

        @Test
        @DisplayName("merges multiple config files in order")
        void mergesMultipleFiles() throws IOException {
            Path userConfig = tempDir.resolve("user-hooks.json");
            Files.writeString(
                    userConfig,
                    """
                    {
                      "hooks": {
                        "SETUP": [
                          { "type": "command", "command": "user-init.sh" }
                        ]
                      }
                    }
                    """);

            Path projectConfig = tempDir.resolve("project-hooks.json");
            Files.writeString(
                    projectConfig,
                    """
                    {
                      "hooks": {
                        "PRE_ACTING": [
                          { "type": "command", "command": "lint.sh" }
                        ]
                      }
                    }
                    """);

            FileBasedHookRegistry registry =
                    new FileBasedHookRegistry(objectMapper, List.of(userConfig, projectConfig));

            List<ExternalHookBinding> bindings = registry.loadAll();
            assertEquals(2, bindings.size());
            assertEquals(HookPhase.SETUP, bindings.get(0).phase());
            assertEquals(HookPhase.PRE_ACTING, bindings.get(1).phase());
        }

        @Test
        @DisplayName("loadAndRegister populates chain bindings")
        void loadAndRegister() throws IOException {
            Path configFile = tempDir.resolve("hooks.json");
            Files.writeString(
                    configFile,
                    """
                    {
                      "hooks": {
                        "SETUP": [
                          { "type": "command", "command": "init.sh" }
                        ]
                      }
                    }
                    """);

            FileBasedHookRegistry registry =
                    new FileBasedHookRegistry(objectMapper, List.of(configFile));

            int count = registry.loadAndRegister(chain);
            assertEquals(1, count);
        }

        @Test
        @DisplayName("missing config file is silently skipped")
        void missingFileSkipped() {
            Path missing = tempDir.resolve("nonexistent.json");
            FileBasedHookRegistry registry =
                    new FileBasedHookRegistry(objectMapper, List.of(missing));

            List<ExternalHookBinding> bindings = registry.loadAll();
            assertTrue(bindings.isEmpty());
        }

        @Test
        @DisplayName("http config parses headers and allowedEnvVars")
        void httpConfigParsed() throws IOException {
            Path configFile = tempDir.resolve("hooks.json");
            Files.writeString(
                    configFile,
                    """
                    {
                      "hooks": {
                        "POST_ACTING": [
                          {
                            "type": "http",
                            "url": "https://hooks.example.com/post",
                            "headers": { "Authorization": "Bearer $API_KEY" },
                            "allowedEnvVars": ["API_KEY"],
                            "timeout": 30
                          }
                        ]
                      }
                    }
                    """);

            FileBasedHookRegistry registry =
                    new FileBasedHookRegistry(objectMapper, List.of(configFile));

            List<ExternalHookBinding> bindings = registry.loadAll();
            assertEquals(1, bindings.size());

            ExternalHookConfig config = bindings.get(0).config();
            assertEquals("http", config.type());
            assertEquals("https://hooks.example.com/post", config.url());
            assertEquals("Bearer $API_KEY", config.headers().get("Authorization"));
            assertEquals(List.of("API_KEY"), config.allowedEnvVars());
            assertEquals(Duration.ofSeconds(30), config.timeout());
        }
    }

    // ── ExternalHookConfig defaults ────────────────────────────────────────

    @Nested
    @DisplayName("ExternalHookConfig defaults")
    class ExternalHookConfigTest {

        @Test
        @DisplayName("command type gets 60s default timeout")
        void commandDefaultTimeout() {
            ExternalHookConfig config =
                    new ExternalHookConfig("command", "echo", null, null, null, null, null, null);
            assertEquals(Duration.ofSeconds(60), config.timeout());
        }

        @Test
        @DisplayName("http type gets 10s default timeout")
        void httpDefaultTimeout() {
            ExternalHookConfig config =
                    new ExternalHookConfig("http", null, "http://x", null, null, null, null, null);
            assertEquals(Duration.ofSeconds(10), config.timeout());
        }

        @Test
        @DisplayName("null headers/allowedEnvVars default to empty")
        void nullDefaults() {
            ExternalHookConfig config =
                    new ExternalHookConfig("command", "echo", null, null, null, null, null, null);
            assertNotNull(config.headers());
            assertTrue(config.headers().isEmpty());
            assertNotNull(config.allowedEnvVars());
            assertTrue(config.allowedEnvVars().isEmpty());
        }
    }
}
