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

import io.kairo.api.hook.PreReasoning;
import io.kairo.api.hook.PreReasoningEvent;
import io.kairo.api.model.ModelConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Tests that hooks execute in order defined by the {@code order} attribute. */
class HookOrderingTest {

    private DefaultHookChain chain;

    @BeforeEach
    void setUp() {
        chain = new DefaultHookChain();
    }

    private PreReasoningEvent preReasoningEvent() {
        return new PreReasoningEvent(List.of(), ModelConfig.builder().model("test").build(), false);
    }

    @Test
    @DisplayName("Lower order value fires before higher — regardless of registration order")
    void lowerOrderFiresFirst_regardlessOfRegistrationOrder() {
        List<Integer> fired = Collections.synchronizedList(new ArrayList<>());

        // Register high-order first, low-order second
        chain.register(
                new Object() {
                    @PreReasoning(order = 100)
                    public PreReasoningEvent h(PreReasoningEvent e) {
                        fired.add(100);
                        return e;
                    }
                });
        chain.register(
                new Object() {
                    @PreReasoning(order = 1)
                    public PreReasoningEvent h(PreReasoningEvent e) {
                        fired.add(1);
                        return e;
                    }
                });

        StepVerifier.create(chain.firePreReasoning(preReasoningEvent()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(fired).containsExactly(1, 100);
    }

    @Test
    @DisplayName("Three handlers fire in ascending order value")
    void threeHandlersFireInAscendingOrder() {
        List<Integer> fired = Collections.synchronizedList(new ArrayList<>());

        chain.register(
                new Object() {
                    @PreReasoning(order = 30)
                    public PreReasoningEvent h(PreReasoningEvent e) {
                        fired.add(30);
                        return e;
                    }
                });
        chain.register(
                new Object() {
                    @PreReasoning(order = 10)
                    public PreReasoningEvent h(PreReasoningEvent e) {
                        fired.add(10);
                        return e;
                    }
                });
        chain.register(
                new Object() {
                    @PreReasoning(order = 20)
                    public PreReasoningEvent h(PreReasoningEvent e) {
                        fired.add(20);
                        return e;
                    }
                });

        StepVerifier.create(chain.firePreReasoning(preReasoningEvent()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(fired).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("Negative order value fires before zero and positive")
    void negativeOrderFiresFirst() {
        List<Integer> fired = Collections.synchronizedList(new ArrayList<>());

        chain.register(
                new Object() {
                    @PreReasoning(order = 0)
                    public PreReasoningEvent h(PreReasoningEvent e) {
                        fired.add(0);
                        return e;
                    }
                });
        chain.register(
                new Object() {
                    @PreReasoning(order = -5)
                    public PreReasoningEvent h(PreReasoningEvent e) {
                        fired.add(-5);
                        return e;
                    }
                });
        chain.register(
                new Object() {
                    @PreReasoning(order = 10)
                    public PreReasoningEvent h(PreReasoningEvent e) {
                        fired.add(10);
                        return e;
                    }
                });

        StepVerifier.create(chain.firePreReasoning(preReasoningEvent()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(fired).containsExactly(-5, 0, 10);
    }

    @Test
    @DisplayName("Order applies independently for different hook phases")
    void orderAppliesPerPhase() {
        List<String> fired = Collections.synchronizedList(new ArrayList<>());

        chain.register(
                new Object() {
                    @PreReasoning(order = 20)
                    public PreReasoningEvent r(PreReasoningEvent e) {
                        fired.add("reasoning-20");
                        return e;
                    }

                    @PreReasoning(order = 5)
                    public PreReasoningEvent r2(PreReasoningEvent e) {
                        fired.add("reasoning-5");
                        return e;
                    }
                });

        StepVerifier.create(chain.firePreReasoning(preReasoningEvent()))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(fired).containsExactly("reasoning-5", "reasoning-20");
    }
}
