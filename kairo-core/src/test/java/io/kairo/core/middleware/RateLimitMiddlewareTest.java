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
package io.kairo.core.middleware;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.middleware.MiddlewareContext;
import io.kairo.api.middleware.MiddlewareRejectException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RateLimitMiddlewareTest {

    private static MiddlewareContext ctx() {
        return MiddlewareContext.of("agent", Msg.of(MsgRole.USER, "hello"));
    }

    @Test
    void withinRatePassesThrough() {
        var limiter = new RateLimitMiddleware(100, 5);
        StepVerifier.create(limiter.handle(ctx(), Mono::just))
                .assertNext(c -> assertThat(c.agentName()).isEqualTo("agent"))
                .verifyComplete();
    }

    @Test
    void burstCapacityIsConsumedSequentially() {
        var limiter = new RateLimitMiddleware(0.001, 3);

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void exceededRateLimitRejectsImmediately() {
        var limiter = new RateLimitMiddleware(0.001, 1);
        limiter.tryAcquire();

        StepVerifier.create(limiter.handle(ctx(), Mono::just))
                .expectErrorSatisfies(
                        e -> {
                            assertThat(e).isInstanceOf(MiddlewareRejectException.class);
                            assertThat(e.getMessage()).contains("Rate limit exceeded");
                        })
                .verify();
    }

    @Test
    void blockOnEmptyWaitsForToken() throws InterruptedException {
        var limiter = new RateLimitMiddleware(1000, 1, true, Duration.ofMillis(200));
        limiter.tryAcquire();

        Thread producer =
                new Thread(
                        () -> {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ignored) {
                            }
                            limiter.availableTokens();
                        });
        producer.start();

        StepVerifier.create(limiter.handle(ctx(), Mono::just))
                .assertNext(c -> assertThat(c).isNotNull())
                .verifyComplete();

        producer.join();
    }

    @Test
    void blockOnEmptyTimesOut() {
        var limiter = new RateLimitMiddleware(0.0001, 1, true, Duration.ofMillis(30));
        limiter.tryAcquire();

        StepVerifier.create(limiter.handle(ctx(), Mono::just))
                .expectErrorSatisfies(
                        e -> {
                            assertThat(e).isInstanceOf(MiddlewareRejectException.class);
                            assertThat(e.getMessage()).contains("timed out");
                        })
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void nameIsRateLimiter() {
        assertThat(new RateLimitMiddleware(10, 10).name()).isEqualTo("rate-limiter");
    }

    @Test
    void illegalArgumentsAreRejected() {
        assertThatThrownBy(() -> new RateLimitMiddleware(0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitMiddleware(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void availableTokensRefillsOverTime() throws InterruptedException {
        var limiter = new RateLimitMiddleware(1000, 5);
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();
        Thread.sleep(5);
        assertThat(limiter.availableTokens()).isGreaterThan(2);
    }
}
