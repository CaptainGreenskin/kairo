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
package io.kairo.core.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.agent.CancellationSignal;
import io.kairo.api.exception.AgentInterruptedException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ProviderRetryCancellationTest {

    @Test
    void monoPolicyStopsWhenCancellationSignalIsSet() {
        AtomicBoolean cancelled = new AtomicBoolean(true);

        Mono<String> source = Mono.never();
        Mono<String> wrapped =
                ProviderRetry.withPolicy(source, "test", t -> false, Duration.ofSeconds(2))
                        .contextWrite(
                                ctx ->
                                        ctx.put(
                                                CancellationSignal.CONTEXT_KEY,
                                                (CancellationSignal) cancelled::get));

        StepVerifier.create(wrapped)
                .expectErrorMatches(
                        e ->
                                e instanceof AgentInterruptedException
                                        && e.getMessage().contains("cancelled"))
                .verify();
    }

    @Test
    void fluxPolicyCompletesWhenCancellationIsNotTriggered() {
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Flux<Integer> wrapped =
                ProviderRetry.withPolicy(
                                Flux.just(1, 2, 3),
                                "test-stream",
                                t -> false,
                                Duration.ofSeconds(2))
                        .contextWrite(
                                ctx ->
                                        ctx.put(
                                                CancellationSignal.CONTEXT_KEY,
                                                (CancellationSignal) cancelled::get));

        StepVerifier.create(wrapped).expectNext(1, 2, 3).verifyComplete();
        assertTrue(!cancelled.get());
    }
}
