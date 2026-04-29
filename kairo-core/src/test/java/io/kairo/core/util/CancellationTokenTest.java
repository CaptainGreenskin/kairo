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
package io.kairo.core.util;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.CancellationSignal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class CancellationTokenTest {

    @Test
    void newTokenIsNotCancelled() {
        CancellationToken token = CancellationToken.notCancelled();
        assertFalse(token.isCancelled());
    }

    @Test
    void cancelSetsFlag() {
        CancellationToken token = CancellationToken.notCancelled();
        token.cancel();
        assertTrue(token.isCancelled());
    }

    @Test
    void multipleCancelsDoNotThrow() {
        CancellationToken token = CancellationToken.notCancelled();
        assertDoesNotThrow(
                () -> {
                    token.cancel();
                    token.cancel();
                    token.cancel();
                });
        assertTrue(token.isCancelled());
    }

    @Test
    void implementsCancellationSignal() {
        CancellationToken token = CancellationToken.notCancelled();
        // Verify it can be used as CancellationSignal
        CancellationSignal signal = token;
        assertFalse(signal.isCancelled());
        token.cancel();
        assertTrue(signal.isCancelled());
    }

    @Test
    void takeUntilTruncatesFluxOnCancellation() {
        CancellationToken token = CancellationToken.notCancelled();

        // Use takeWhile to synchronously observe cancellation at each element boundary
        Flux<String> flux =
                Flux.just("a", "b", "c", "d", "e")
                        .takeWhile(s -> !token.isCancelled())
                        .doOnNext(
                                s -> {
                                    if ("b".equals(s)) {
                                        token.cancel();
                                    }
                                });

        StepVerifier.create(flux)
                .expectNext("a", "b")
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void alreadyCancelledTokenTruncatesImmediately() {
        CancellationToken token = CancellationToken.notCancelled();
        token.cancel();

        Flux<String> flux = Flux.just("x", "y", "z").takeWhile(s -> !token.isCancelled());

        // All elements are filtered out since already cancelled
        StepVerifier.create(flux).expectComplete().verify(Duration.ofSeconds(2));
    }

    @Test
    void threadSafety() throws InterruptedException {
        CancellationToken token = new CancellationToken();
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(token::cancel);
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertTrue(token.isCancelled());
    }
}
