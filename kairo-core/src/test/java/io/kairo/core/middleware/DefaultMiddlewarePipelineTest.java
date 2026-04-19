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
import io.kairo.api.middleware.Middleware;
import io.kairo.api.middleware.MiddlewareChain;
import io.kairo.api.middleware.MiddlewareContext;
import io.kairo.api.middleware.MiddlewareOrder;
import io.kairo.api.middleware.MiddlewareRejectException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultMiddlewarePipelineTest {

    private MiddlewareContext testContext() {
        return MiddlewareContext.of("test-agent", Msg.of(MsgRole.USER, "hello"));
    }

    // ---- Topology sort ----

    @Nested
    @DisplayName("Topology sort")
    class TopologySort {

        @Test
        @DisplayName("after/before constraints are satisfied")
        void orderingConstraintsSatisfied() {
            // A before C, C before B => A, C, B
            List<String> order = new ArrayList<>();
            DefaultMiddlewarePipeline pipeline =
                    new DefaultMiddlewarePipeline(
                            List.of(
                                    new OrderTrackingC(order),
                                    new OrderTrackingA(order),
                                    new OrderTrackingB(order)));

            StepVerifier.create(pipeline.execute(testContext()))
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(order).containsExactly("a", "c", "b");
        }

        @Test
        @DisplayName("unordered middleware can appear anywhere")
        void unorderedMiddlewareFlexible() {
            List<String> order = new ArrayList<>();
            DefaultMiddlewarePipeline pipeline =
                    new DefaultMiddlewarePipeline(
                            List.of(
                                    new OrderTrackingUnordered("x", order),
                                    new OrderTrackingFirst(order),
                                    new OrderTrackingUnordered("y", order)));

            StepVerifier.create(pipeline.execute(testContext()))
                    .expectNextCount(1)
                    .verifyComplete();
            assertThat(order).contains("first");
        }

        @Test
        @DisplayName("cycle detected at construction")
        void cycleDetected() {
            assertThatThrownBy(
                            () ->
                                    new DefaultMiddlewarePipeline(
                                            List.of(new CycleA(), new CycleB())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Circular dependency");
        }

        @Test
        @DisplayName("reference to unknown name fails at construction")
        void unknownNameFails() {
            assertThatThrownBy(
                            () -> new DefaultMiddlewarePipeline(List.of(new ReferencesUnknown())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no middleware with name()");
        }

        @Test
        @DisplayName("duplicate names fail at construction")
        void duplicateNamesFail() {
            assertThatThrownBy(
                            () ->
                                    new DefaultMiddlewarePipeline(
                                            List.of(
                                                    new SimpleMiddleware("dup"),
                                                    new SimpleMiddleware("dup"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Duplicate middleware name");
        }

        @Test
        @DisplayName("null or blank name fails at construction")
        void blankNameFails() {
            Middleware blank =
                    new Middleware() {
                        @Override
                        public String name() {
                            return "";
                        }

                        @Override
                        public Mono<MiddlewareContext> handle(
                                MiddlewareContext ctx, MiddlewareChain chain) {
                            return chain.next(ctx);
                        }
                    };
            assertThatThrownBy(() -> new DefaultMiddlewarePipeline(List.of(blank)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("null or blank name()");
        }
    }

    // ---- Execution ----

    @Nested
    @DisplayName("Pipeline execution")
    class Execution {

        @Test
        @DisplayName("empty pipeline passes through")
        void emptyPipeline() {
            DefaultMiddlewarePipeline pipeline = new DefaultMiddlewarePipeline(List.of());
            StepVerifier.create(pipeline.execute(testContext()))
                    .assertNext(ctx -> assertThat(ctx.input().text()).isEqualTo("hello"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("attributes flow between middleware")
        void attributesFlow() {
            DefaultMiddlewarePipeline pipeline =
                    new DefaultMiddlewarePipeline(
                            List.of(new AttrWriterMiddleware(), new AttrReaderMiddleware()));
            StepVerifier.create(pipeline.execute(testContext()))
                    .assertNext(ctx -> assertThat(ctx.attributes().get("key")).isEqualTo("value"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("short-circuit stops subsequent middleware")
        void shortCircuit() {
            StringBuilder log = new StringBuilder();

            Middleware rejector =
                    new Middleware() {
                        @Override
                        public String name() {
                            return "rejector";
                        }

                        @Override
                        public Mono<MiddlewareContext> handle(
                                MiddlewareContext ctx, MiddlewareChain chain) {
                            log.append("rejector ");
                            return Mono.error(new MiddlewareRejectException("rejector", "blocked"));
                        }
                    };
            Middleware after =
                    new Middleware() {
                        @Override
                        public String name() {
                            return "after";
                        }

                        @Override
                        public Mono<MiddlewareContext> handle(
                                MiddlewareContext ctx, MiddlewareChain chain) {
                            log.append("after ");
                            return chain.next(ctx);
                        }
                    };

            DefaultMiddlewarePipeline pipeline =
                    new DefaultMiddlewarePipeline(List.of(rejector, after));
            StepVerifier.create(pipeline.execute(testContext()))
                    .expectError(MiddlewareRejectException.class)
                    .verify();

            assertThat(log.toString()).isEqualTo("rejector ");
        }

        @Test
        @DisplayName("MiddlewareRejectException preserves middleware name")
        void rejectExceptionHasName() {
            Middleware rejector =
                    new Middleware() {
                        @Override
                        public String name() {
                            return "auth";
                        }

                        @Override
                        public Mono<MiddlewareContext> handle(
                                MiddlewareContext ctx, MiddlewareChain chain) {
                            return Mono.error(new MiddlewareRejectException("auth", "bad token"));
                        }
                    };

            DefaultMiddlewarePipeline pipeline = new DefaultMiddlewarePipeline(List.of(rejector));
            StepVerifier.create(pipeline.execute(testContext()))
                    .expectErrorSatisfies(
                            e -> {
                                MiddlewareRejectException ex = (MiddlewareRejectException) e;
                                assertThat(ex.middlewareName()).isEqualTo("auth");
                                assertThat(ex.getMessage()).contains("bad token");
                            })
                    .verify();
        }

        @Test
        @DisplayName("middleware not calling chain.next() short-circuits")
        void noChainNextShortCircuits() {
            List<String> log = new ArrayList<>();

            Middleware stopper =
                    new Middleware() {
                        @Override
                        public String name() {
                            return "stopper";
                        }

                        @Override
                        public Mono<MiddlewareContext> handle(
                                MiddlewareContext ctx, MiddlewareChain chain) {
                            log.add("stopper");
                            return Mono.just(ctx); // does NOT call chain.next()
                        }
                    };
            Middleware after =
                    new Middleware() {
                        @Override
                        public String name() {
                            return "after";
                        }

                        @Override
                        public Mono<MiddlewareContext> handle(
                                MiddlewareContext ctx, MiddlewareChain chain) {
                            log.add("after");
                            return chain.next(ctx);
                        }
                    };

            DefaultMiddlewarePipeline pipeline =
                    new DefaultMiddlewarePipeline(List.of(stopper, after));
            StepVerifier.create(pipeline.execute(testContext()))
                    .expectNextCount(1)
                    .verifyComplete();

            assertThat(log).containsExactly("stopper");
        }
    }

    // ---- Tracking middleware with @MiddlewareOrder ----

    @MiddlewareOrder(after = {})
    static class OrderTrackingFirst implements Middleware {
        private final List<String> order;

        OrderTrackingFirst(List<String> order) {
            this.order = order;
        }

        @Override
        public String name() {
            return "first";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            order.add("first");
            return chain.next(ctx);
        }
    }

    @MiddlewareOrder(
            after = {},
            before = {"c", "b"})
    static class OrderTrackingA implements Middleware {
        private final List<String> order;

        OrderTrackingA(List<String> order) {
            this.order = order;
        }

        @Override
        public String name() {
            return "a";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            order.add("a");
            return chain.next(ctx);
        }
    }

    @MiddlewareOrder(after = {"c"})
    static class OrderTrackingB implements Middleware {
        private final List<String> order;

        OrderTrackingB(List<String> order) {
            this.order = order;
        }

        @Override
        public String name() {
            return "b";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            order.add("b");
            return chain.next(ctx);
        }
    }

    @MiddlewareOrder(
            after = {"a"},
            before = {"b"})
    static class OrderTrackingC implements Middleware {
        private final List<String> order;

        OrderTrackingC(List<String> order) {
            this.order = order;
        }

        @Override
        public String name() {
            return "c";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            order.add("c");
            return chain.next(ctx);
        }
    }

    static class OrderTrackingUnordered implements Middleware {
        private final String n;
        private final List<String> order;

        OrderTrackingUnordered(String name, List<String> order) {
            this.n = name;
            this.order = order;
        }

        @Override
        public String name() {
            return n;
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            order.add(n);
            return chain.next(ctx);
        }
    }

    // ---- Error-case middleware ----

    static class SimpleMiddleware implements Middleware {
        private final String n;

        SimpleMiddleware(String name) {
            this.n = name;
        }

        @Override
        public String name() {
            return n;
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            return chain.next(ctx);
        }
    }

    @MiddlewareOrder(after = {"cycle-b"})
    static class CycleA implements Middleware {
        @Override
        public String name() {
            return "cycle-a";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            return chain.next(ctx);
        }
    }

    @MiddlewareOrder(after = {"cycle-a"})
    static class CycleB implements Middleware {
        @Override
        public String name() {
            return "cycle-b";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            return chain.next(ctx);
        }
    }

    @MiddlewareOrder(after = {"does-not-exist"})
    static class ReferencesUnknown implements Middleware {
        @Override
        public String name() {
            return "ref-unknown";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            return chain.next(ctx);
        }
    }

    // ---- Attribute flow test middleware ----

    @MiddlewareOrder(before = {"attr-reader"})
    static class AttrWriterMiddleware implements Middleware {
        @Override
        public String name() {
            return "attr-writer";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            return chain.next(ctx.withAttribute("key", "value"));
        }
    }

    @MiddlewareOrder(after = {"attr-writer"})
    static class AttrReaderMiddleware implements Middleware {
        @Override
        public String name() {
            return "attr-reader";
        }

        @Override
        public Mono<MiddlewareContext> handle(MiddlewareContext ctx, MiddlewareChain chain) {
            return chain.next(ctx);
        }
    }
}
