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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.hook.ExternalHookBinding;
import io.kairo.api.hook.ExternalHookConfig;
import io.kairo.api.hook.ExternalHookExecutor;
import io.kairo.api.hook.HookEvent;
import io.kairo.api.hook.HookPhase;
import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.SetupEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Regression guard for the observability bug where {@link HttpHookExecutor} and {@link
 * CommandHookExecutor} swallowed their own errors with {@code .onErrorResume(... -> proceed)},
 * preventing {@link DefaultHookChain#recordExternalHookFailure} from ever firing.
 *
 * <p>The executors now propagate failures; the chain catches them with its own {@code
 * .onErrorResume} (in {@code fireExternalHooks}) so the failure is recorded in the observability
 * pipeline (tracer span + chain stats + observer callback) before the chain degrades the result to
 * CONTINUE — preserving previous user-visible behavior.
 */
class ExternalHookFailurePropagationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void executorReturningMonoError_isCapturedByChainStats() {
        // Models the post-fix contract: any external executor that surfaces an error via
        // Mono.error(...) (as the real HTTP / Command executors now do for I/O failures) must be
        // observed by the chain. A shell-wrapped missing-binary in CommandHookExecutor exits 127
        // gracefully — not the path we need to exercise — so we use a synthetic failing executor
        // to lock in the chain-side contract.
        DefaultHookChain chain = new DefaultHookChain();
        chain.registerExecutor(
                new ExternalHookExecutor() {
                    @Override
                    public String type() {
                        return "synthetic-failure";
                    }

                    @Override
                    public <T extends HookEvent> Mono<HookResult<T>> execute(
                            T event, ExternalHookConfig config) {
                        return Mono.error(new RuntimeException("simulated executor failure"));
                    }
                });
        chain.registerExternalBinding(
                new ExternalHookBinding(
                        HookPhase.SETUP,
                        new ExternalHookConfig(
                                "synthetic-failure",
                                "n/a",
                                null,
                                Map.of(),
                                List.of(),
                                Duration.ofSeconds(5),
                                null,
                                null)));

        SetupEvent event = new SetupEvent("s1", "init");

        // Chain still degrades to CONTINUE for the caller, but the failure must be recorded.
        StepVerifier.create(chain.firePhase(HookPhase.SETUP, event))
                .expectNextCount(1)
                .verifyComplete();

        DefaultHookChain.HookChainStats stats = chain.snapshot();
        assertThat(stats.externalHookFailures())
                .as("Executor Mono.error must increment externalHookFailures")
                .isEqualTo(1L);
        assertThat(stats.failuresByPhase()).containsEntry("SETUP", 1L);
    }

    @Test
    void httpExecutor_invalidUrl_isCapturedByChainStats() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.registerExecutor(new HttpHookExecutor(mapper));
        chain.registerExternalBinding(
                new ExternalHookBinding(
                        HookPhase.SETUP,
                        new ExternalHookConfig(
                                "http",
                                null,
                                // Unroutable host — httpClient.send throws ConnectException.
                                "http://127.0.0.1:1/this-port-is-not-listening",
                                Map.of(),
                                List.of(),
                                Duration.ofSeconds(2),
                                null,
                                null)));

        SetupEvent event = new SetupEvent("s1", "init");

        StepVerifier.create(chain.firePhase(HookPhase.SETUP, event))
                .expectNextCount(1)
                .verifyComplete();

        DefaultHookChain.HookChainStats stats = chain.snapshot();
        assertThat(stats.externalHookFailures())
                .as("HTTP hook connection failure must increment externalHookFailures")
                .isEqualTo(1L);
        assertThat(stats.failuresByPhase()).containsEntry("SETUP", 1L);
    }
}
